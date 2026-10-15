//! # Boox Notes Android FFI Library
//!
//! This library provides JNI bindings for parsing and rendering Boox Notes on Android devices.
//! It bridges the Rust `boox-note-parser` library to Kotlin/Java code via the JNI interface.
//!
//! ## Architecture Overview
//!
//! Boox Notes can be accessed in two ways:
//!
//! 1. **Exported .note files** (ZIP archives): These are self-contained files that users
//!    can export from the Boox Notes app. The original parser was designed for these.
//!
//! 2. **On-device .ksync storage**: Notes stored directly on the device filesystem at
//!    `/storage/emulated/0/.ksync/`. This is how the Boox Notes app stores notes internally.
//!    The on-device format splits data across multiple directories:
//!    - `.ksync/document/{noteId}/shape/` - Shape data (stroke metadata) as ZIP files
//!    - `.ksync/point/{noteId}/{pageId}/` - Point data (actual stroke coordinates)
//!    - `.ksync/document/{noteId}/pageModel/pb/` - Page dimensions and settings
//!
//! ## Key Data Structures
//!
//! - **ShapeGroup**: Contains metadata about strokes (stroke ID, points ID, z-order, etc.)
//! - **PointsFile**: Contains the actual coordinate data for strokes
//! - **Shape.points_id**: Links a shape to its corresponding PointsFile
//! - **Shape.stroke_id**: Identifies a specific stroke within a PointsFile
//!
//! ## Rendering Pipeline
//!
//! 1. Load shape files for the page (contains stroke metadata)
//! 2. Load point files for the page (contains stroke coordinates)
//! 3. For each shape, find the matching PointsFile using `points_id`
//! 4. Within that PointsFile, find the stroke data using `stroke_id`
//! 5. Render the stroke points to a canvas using raqote
//!
//! ## Usage from Kotlin
//!
//! ```kotlin
//! // Check if note exists on device
//! val existsJson = BooxNative.noteExistsOnDevice(KSYNC_BASE_PATH, uniqueId)
//!
//! // List pages for a note
//! val pagesJson = BooxNative.listPagesOnDevice(KSYNC_BASE_PATH, uniqueId)
//!
//! // Render a page
//! val bitmap = BooxNative.renderPageOnDevice(KSYNC_BASE_PATH, uniqueId, pageId)
//! ```
//!
//! ## Important Notes for Future Development
//!
//! - The `DisplayScale` struct in shape.rs was modified to use `#[serde(default)]` on all
//!   fields because the on-device format has different JSON fields than exported files.
//! - Canvas dimensions are hardcoded to 1860x2480 (Boox Note Air dimensions). For other
//!   devices, you may need to parse the pageModel for exact dimensions.
//! - The KSYNC_BASE_PATH is typically `/storage/emulated/0/.ksync` but may vary.

use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{Cursor, Read};
use std::path::PathBuf;

use rusqlite::Connection;

use boox_note_parser::{
    error::Result as ParserResult,
    id::{NoteUuid, PageUuid},
    PageModelGroup,
    points::PointsFile,
    shape::ShapeGroup,
    NoteFile,
};
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jobject, jstring};
use jni::JNIEnv;
use raqote::{DrawOptions, DrawTarget, Source, StrokeStyle};
use serde::Serialize;

// ============================================================================
// SECTION 1: EXPORTED .NOTE FILE FUNCTIONS
// ============================================================================
// These functions work with exported .note ZIP files (not on-device storage).
// They are kept for backwards compatibility but are NOT typically used for
// on-device notes. Use the "OnDevice" functions instead for live notes.
// ============================================================================

/// Opens and parses an exported .note file (ZIP archive format).
///
/// # Arguments
/// * `path` - Full filesystem path to the .note file
///
/// # Returns
/// * `ParserResult<NoteFile>` - Parsed note file or error
fn read_note_file(path: &str) -> ParserResult<NoteFile<File>> {
    let file = File::open(path)?;
    NoteFile::read(file)
}

/// JSON response structure for note listing.
#[derive(Serialize)]
struct NoteInfo {
    note_id: String,
    name: String,
}

/// JNI function: Lists all notes contained in an exported .note file.
///
/// This is for exported ZIP archives, NOT on-device storage.
///
/// # Parameters (from Kotlin)
/// * `path: String` - Path to the .note file
///
/// # Returns
/// * JSON array string: `[{"note_id": "...", "name": "..."}]`
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_listNotesInFile(
    mut env: JNIEnv,
    _class: JClass,
    j_path: JString,
) -> jstring {
    let path: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let result = (|| -> ParserResult<String> {
        let note_file = read_note_file(&path)?;
        let notes: Vec<NoteInfo> = note_file
            .list_notes()
            .into_iter()
            .map(|(id, name)| NoteInfo {
                note_id: id.to_hyphenated_string(),
                name,
            })
            .collect();
        Ok(serde_json::to_string(&notes).unwrap_or_else(|_| "[]".into()))
    })();

    let json = result.unwrap_or_else(|_| "[]".into());
    env.new_string(json).unwrap().into_raw()
}

/// JSON response structure for page listing (exported format).
#[derive(Serialize)]
struct PagesInfo {
    active: Vec<String>,
    reserved: Vec<String>,
    detached: Vec<String>,
}

/// JNI function: Lists pages in an exported .note file.
///
/// This is for exported ZIP archives, NOT on-device storage.
///
/// # Parameters (from Kotlin)
/// * `path: String` - Path to the .note file
/// * `noteId: String` - UUID of the note within the file
///
/// # Returns
/// * JSON object: `{"active": [...], "reserved": [...], "detached": [...]}`
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_listPages(
    mut env: JNIEnv,
    _class: JClass,
    j_path: JString,
    j_note_id: JString,
) -> jstring {
    let path: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let note_id: String = match env.get_string(&j_note_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let result = (|| -> ParserResult<String> {
        let note_file = read_note_file(&path)?;
        let note_uuid = NoteUuid::from_str(&note_id)?;
        let note = match note_file.get_note(&note_uuid) {
            Some(n) => n,
            None => return Ok("{}".into()),
        };

        let to_strings =
            |ids: &[PageUuid]| ids.iter().map(|p| p.to_hyphenated_string()).collect::<Vec<_>>();

        let pages = PagesInfo {
            active: to_strings(note.active_pages()),
            reserved: to_strings(note.reserved_pages()),
            detached: to_strings(note.detached_pages()),
        };

        Ok(serde_json::to_string(&pages).unwrap_or_else(|_| "{}".into()))
    })();

    let json = result.unwrap_or_else(|_| "{}".into());
    env.new_string(json).unwrap().into_raw()
}

/// JNI function: Renders a page from an exported .note file.
///
/// This is for exported ZIP archives, NOT on-device storage.
/// Use `renderPageOnDevice` for on-device notes.
///
/// # Parameters (from Kotlin)
/// * `path: String` - Path to the .note file
/// * `noteId: String` - UUID of the note
/// * `pageId: String` - UUID of the page to render
///
/// # Returns
/// * `BooxBitmap` object or null on error
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_renderPage(
    mut env: JNIEnv,
    _class: JClass,
    j_path: JString,
    j_note_id: JString,
    j_page_id: JString,
) -> jobject {
    let path: String = match env.get_string(&j_path) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null().into_raw(),
    };
    let note_id: String = match env.get_string(&j_note_id) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null().into_raw(),
    };
    let page_id: String = match env.get_string(&j_page_id) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null().into_raw(),
    };

    let result = (|| -> ParserResult<(i32, i32, i32, Vec<u8>)> {
        let note_file = read_note_file(&path)?;
        let note_uuid = NoteUuid::from_str(&note_id)?;
        let mut note = match note_file.get_note(&note_uuid) {
            Some(n) => n,
            None => return Ok((0, 0, 0, Vec::new())),
        };

        let page_uuid = PageUuid::from_str(&page_id)?;
        let mut page = match note.get_page(&page_uuid) {
            Some(p) => p,
            None => return Ok((0, 0, 0, Vec::new())),
        };

        // Use the parser's built-in render function for exported files
        let dt = page.render()?;

        // Convert the rendered image to RGBA byte array
        let width = dt.width();
        let height = dt.height();
        let stride = width * 4;

        let data = dt.get_data();
        let mut buf = Vec::with_capacity((height * stride) as usize);
        for y in 0..height {
            for x in 0..width {
                let pixel = data[(y * width + x) as usize];
                // Extract ARGB components and reorder to RGBA
                let a = ((pixel >> 24) & 0xff) as u8;
                let r = ((pixel >> 16) & 0xff) as u8;
                let g = ((pixel >> 8) & 0xff) as u8;
                let b = (pixel & 0xff) as u8;
                
                buf.push(r);
                buf.push(g);
                buf.push(b);
                buf.push(255);  // A - always fully opaque
            }
        }

        Ok((width, height, stride, buf))
    })();

    let (width, height, stride, buf) = match result {
        Ok(v) => v,
        Err(_) => return JObject::null().into_raw(),
    };

    if width == 0 || height == 0 || buf.is_empty() {
        return JObject::null().into_raw();
    }

    // Create JNI byte array and copy pixel data
    let i8buf: Vec<i8> = buf.iter().map(|b| *b as i8).collect();

    let byte_array: JByteArray = env
        .new_byte_array(i8buf.len() as i32)
        .expect("Failed to allocate byte array");
    env.set_byte_array_region(&byte_array, 0, &i8buf)
        .expect("Failed to copy pixels");

    // Create and return BooxBitmap object
    let cls = match env.find_class("com/boox/day/booxnotes/BooxBitmap") {
        Ok(c) => c,
        Err(_) => return JObject::null().into_raw(),
    };

    let obj = env
        .new_object(
            cls,
            "(III[B)V",
            &[
                JValue::from(width as i32),
                JValue::from(height as i32),
                JValue::from(stride as i32),
                JValue::Object(&JObject::from(byte_array)),
            ],
        )
        .unwrap_or_else(|_| JObject::null());

    obj.into_raw()
}

// ============================================================================
// SECTION 2: ON-DEVICE .KSYNC STORAGE FUNCTIONS
// ============================================================================
// These functions work with notes stored on the device filesystem.
// This is the PRIMARY way to access notes for a calendar app integration.
//
// The .ksync directory structure:
// /storage/emulated/0/.ksync/
//   ├── document/{noteId}/
//   │   ├── shape/                    <- Shape data (stroke metadata)
//   │   │   └── {pageId}#{shapeGroupId}#{timestamp}.zip
//   │   └── pageModel/pb/             <- Page dimensions
//   │       └── {pageModelId}
//   └── point/{noteId}/{pageId}/      <- Point data (stroke coordinates)
//       └── {pageId}#{pointsId}#points
// ============================================================================

/// JSON response structure for on-device page information.
///
/// # Fields
/// * `page_id` - UUID of the page
/// * `shape_files` - List of shape ZIP files for this page
/// * `point_files` - List of point files for this page
/// * `has_page_model` - Whether page model data exists
#[derive(Serialize)]
struct OnDevicePageInfo {
    page_id: String,
    shape_files: Vec<String>,
    point_files: Vec<String>,
    has_page_model: bool,
}

/// JSON response structure for on-device note information.
///
/// # Fields
/// * `unique_id` - The note's unique ID (from content provider)
/// * `pages` - List of pages with their associated files
/// * `document_path` - Full path to document directory
/// * `point_path` - Full path to point directory
#[derive(Serialize)]
struct OnDeviceNoteInfo {
    unique_id: String,
    pages: Vec<OnDevicePageInfo>,
    document_path: String,
    point_path: String,
}

/// JNI function: Lists pages available for a note from on-device .ksync storage.
///
/// This scans the filesystem to find all shape and point files for a note.
/// Use this to discover what pages exist and what data is available.
///
/// # Parameters (from Kotlin)
/// * `ksyncBase: String` - Base path to .ksync folder (e.g., "/storage/emulated/0/.ksync")
/// * `uniqueId: String` - The note's uniqueId from the content provider
///
/// # Returns
/// * JSON object with page information or error
///
/// # Example Response
/// ```json
/// {
///   "unique_id": "abc123",
///   "pages": [
///     {
///       "page_id": "def456",
///       "shape_files": ["def456#ghi789#1234567890.zip"],
///       "point_files": ["def456#jkl012#points"],
///       "has_page_model": true
///     }
///   ],
///   "document_path": "/storage/emulated/0/.ksync/document/abc123",
///   "point_path": "/storage/emulated/0/.ksync/point/abc123"
/// }
/// ```
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_listPagesOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
    j_unique_id: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let unique_id: String = match env.get_string(&j_unique_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let result = list_pages_on_device_impl(&ksync_base, &unique_id);
    let json = match result {
        Ok(info) => serde_json::to_string(&info).unwrap_or_else(|_| "{}".into()),
        Err(e) => format!("{{\"error\": \"{}\"}}", e),
    };

    env.new_string(json).unwrap().into_raw()
}

/// Implementation: Scans filesystem for page data.
///
/// # Algorithm
/// 1. Build paths to document and point directories
/// 2. Scan shape directory for files matching `{pageId}#*.zip`
/// 3. Scan point directory for subdirectories (each is a page)
/// 4. Within each page subdir, find files ending with `#points`
/// 5. Check for page model data in pageModel/pb/
fn list_pages_on_device_impl(ksync_base: &str, unique_id: &str) -> Result<OnDeviceNoteInfo, String> {
    // Build paths to the note's document and point directories
    let document_path = PathBuf::from(ksync_base).join("document").join(unique_id);
    let point_path = PathBuf::from(ksync_base).join("point").join(unique_id);

    // Verify at least one directory exists
    if !document_path.exists() && !point_path.exists() {
        return Err(format!("Note not found at {} or {}", document_path.display(), point_path.display()));
    }

    // Collect pages by scanning both directories
    let mut pages: HashMap<String, OnDevicePageInfo> = HashMap::new();

    // STEP 1: Scan shape files in document/{id}/shape/
    // Shape files are named: {pageId}#{shapeGroupId}#{timestamp}.zip
    let shape_dir = document_path.join("shape");
    if shape_dir.exists() {
        if let Ok(entries) = fs::read_dir(&shape_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                // Extract page ID from filename (first segment before #)
                if let Some(page_id) = name.split('#').next() {
                    let page = pages.entry(page_id.to_string()).or_insert_with(|| OnDevicePageInfo {
                        page_id: page_id.to_string(),
                        shape_files: Vec::new(),
                        point_files: Vec::new(),
                        has_page_model: false,
                    });
                    page.shape_files.push(name);
                }
            }
        }
    }

    // STEP 2: Scan point files in point/{id}/{pageId}/
    // Point directory structure: point/{noteId}/{pageId}/{pageId}#{pointsId}#points
    if point_path.exists() {
        if let Ok(page_dirs) = fs::read_dir(&point_path) {
            for page_dir in page_dirs.flatten() {
                if page_dir.path().is_dir() {
                    let page_id = page_dir.file_name().to_string_lossy().to_string();
                    let page = pages.entry(page_id.clone()).or_insert_with(|| OnDevicePageInfo {
                        page_id: page_id.clone(),
                        shape_files: Vec::new(),
                        point_files: Vec::new(),
                        has_page_model: false,
                    });

                    // Find point files within the page directory
                    if let Ok(point_files) = fs::read_dir(page_dir.path()) {
                        for pf in point_files.flatten() {
                            let name = pf.file_name().to_string_lossy().to_string();
                            if name.ends_with("#points") {
                                page.point_files.push(name);
                            }
                        }
                    }
                }
            }
        }
    }

    // STEP 3: Resolve page model availability per page
    for page in pages.values_mut() {
        page.has_page_model = load_page_dimensions(&document_path, &page.page_id).is_some();
    }

    let mut page_list: Vec<OnDevicePageInfo> = pages.into_values().collect();
    page_list.sort_by(|a, b| a.page_id.cmp(&b.page_id));

    Ok(OnDeviceNoteInfo {
        unique_id: unique_id.to_string(),
        pages: page_list,
        document_path: document_path.to_string_lossy().to_string(),
        point_path: point_path.to_string_lossy().to_string(),
    })
}

fn load_page_dimensions(document_path: &PathBuf, page_id: &str) -> Option<(i32, i32)> {
    let page_model_dir = document_path.join("pageModel").join("pb");
    if !page_model_dir.exists() || !page_model_dir.is_dir() {
        return None;
    }

    let entries = fs::read_dir(&page_model_dir).ok()?;
    for entry in entries.flatten() {
        let file = match File::open(entry.path()) {
            Ok(file) => file,
            Err(_) => continue,
        };
        let page_model_group = match PageModelGroup::read(file) {
            Ok(group) => group,
            Err(_) => continue,
        };

        for page_model in page_model_group.page_models {
            let matches_page = page_model.page_id.to_simple_string() == page_id
                || page_model.page_id.to_hyphenated_string() == page_id;
            if !matches_page {
                continue;
            }

            let width = (page_model.dimensions.right - page_model.dimensions.left)
                .abs()
                .round()
                .max(1.0) as i32;
            let height = (page_model.dimensions.bottom - page_model.dimensions.top)
                .abs()
                .round()
                .max(1.0) as i32;
            return Some((width, height));
        }
    }

    None
}

/// JNI function: Renders a page directly from on-device .ksync storage.
///
/// This is the PRIMARY rendering function for calendar app integration.
/// It reads shape and point files directly from the filesystem and renders
/// the strokes to a bitmap.
///
/// # Parameters (from Kotlin)
/// * `ksyncBase: String` - Base path to .ksync folder
/// * `uniqueId: String` - The note's uniqueId from content provider
/// * `pageId: String` - The page ID (from pageNameList in content provider)
///
/// # Returns
/// * `BooxBitmap` object with rendered page, or null on error
///
/// # Rendering Algorithm
/// 1. Load all shape files for the page (contain stroke metadata)
/// 2. Load all point files for the page (contain stroke coordinates)
/// 3. For each shape, match it to a PointsFile using `points_id`
/// 4. For each matched shape, find the stroke data using `stroke_id`
/// 5. Render each stroke to the canvas
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_renderPageOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
    j_unique_id: JString,
    j_page_id: JString,
) -> jobject {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null().into_raw(),
    };
    let unique_id: String = match env.get_string(&j_unique_id) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null().into_raw(),
    };
    let page_id: String = match env.get_string(&j_page_id) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null().into_raw(),
    };

    let result = render_page_on_device_impl(&ksync_base, &unique_id, &page_id);

    let (width, height, stride, buf) = match result {
        Ok(v) => v,
        Err(e) => {
            eprintln!("Render error: {}", e);
            return JObject::null().into_raw();
        }
    };

    if width == 0 || height == 0 || buf.is_empty() {
        return JObject::null().into_raw();
    }

    // Convert to signed byte array for JNI
    let i8buf: Vec<i8> = buf.iter().map(|b| *b as i8).collect();

    // Create JNI byte array
    let byte_array: JByteArray = match env.new_byte_array(i8buf.len() as i32) {
        Ok(arr) => arr,
        Err(_) => return JObject::null().into_raw(),
    };

    if env.set_byte_array_region(&byte_array, 0, &i8buf).is_err() {
        return JObject::null().into_raw();
    }

    // Create BooxBitmap object to return
    let cls = match env.find_class("com/boox/day/booxnotes/BooxBitmap") {
        Ok(c) => c,
        Err(_) => return JObject::null().into_raw(),
    };

    let obj = env
        .new_object(
            cls,
            "(III[B)V",  // Constructor signature: (int width, int height, int stride, byte[] pixels)
            &[
                JValue::from(width as i32),
                JValue::from(height as i32),
                JValue::from(stride as i32),
                JValue::Object(&JObject::from(byte_array)),
            ],
        )
        .unwrap_or_else(|_| JObject::null());

    obj.into_raw()
}

/// Implementation: Renders a page from on-device storage.
///
/// This is the core rendering logic for on-device notes.
///
/// # Returns
/// * `Ok((width, height, stride, rgba_buffer))` - Rendered image data
/// * `Err(message)` - Error description
fn render_page_on_device_impl(
    ksync_base: &str,
    unique_id: &str,
    page_id: &str,
) -> Result<(i32, i32, i32, Vec<u8>), String> {
    let document_path = PathBuf::from(ksync_base).join("document").join(unique_id);
    let point_path = PathBuf::from(ksync_base).join("point").join(unique_id);

    // Prefer the real page size from pageModel; fall back to the Note Air baseline.
    let (width, height) = load_page_dimensions(&document_path, page_id)
        .unwrap_or((1860, 2480));

    // Create drawing canvas using raqote library
    let mut draw_target = DrawTarget::new(width, height);
    let draw_options = DrawOptions::new();

    // Fill with white background
    draw_target.fill_rect(
        0.0,
        0.0,
        width as f32,
        height as f32,
        &Source::Solid(raqote::Color::new(255, 255, 255, 255).into()),
        &draw_options,
    );

    // ========================================================================
    // STEP 1: Load shape files for this page
    // Shape files contain stroke metadata including:
    // - stroke_id: identifies the stroke within a PointsFile
    // - points_id: identifies which PointsFile contains the stroke data
    // - z_order: rendering order
    // - bounding box, line style, etc.
    // ========================================================================
    let mut shape_groups: Vec<ShapeGroup> = Vec::new();
    let shape_dir = document_path.join("shape");

    if shape_dir.exists() {
        if let Ok(entries) = fs::read_dir(&shape_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                // Shape files are named: {pageId}#{shapeGroupId}#{timestamp}.zip
                // Only load files for the requested page
                if name.starts_with(page_id) && name.ends_with(".zip") {
                    if let Ok(mut file) = File::open(entry.path()) {
                        let mut buf = Vec::new();
                        if file.read_to_end(&mut buf).is_ok() {
                            // Parse the shape ZIP file
                            if let Ok(sg) = ShapeGroup::read(Cursor::new(buf)) {
                                shape_groups.push(sg);
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // STEP 2: Load point files for this page
    // Point files contain the actual stroke coordinate data.
    // Each PointsFile has a header with a points_id that shapes reference.
    // The file contains a stroke table mapping stroke_id -> coordinates.
    // ========================================================================
    let mut points_files: Vec<PointsFile> = Vec::new();
    let page_point_dir = point_path.join(page_id);

    if page_point_dir.exists() {
        if let Ok(entries) = fs::read_dir(&page_point_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                // Point files are named: {pageId}#{pointsId}#points
                if name.ends_with("#points") {
                    if let Ok(mut file) = File::open(entry.path()) {
                        let mut buf = Vec::new();
                        if file.read_to_end(&mut buf).is_ok() {
                            // Parse the binary point file
                            if let Ok(pf) = PointsFile::read(Cursor::new(buf)) {
                                points_files.push(pf);
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // STEP 3: Render strokes
    // For each shape, we need to:
    // 1. Find the PointsFile that matches the shape's points_id
    // 2. Within that file, get the stroke data for the shape's stroke_id
    // 3. Render the stroke points to the canvas using ACTUAL stroke properties
    // ========================================================================
    // Minimum stroke width for visibility (original strokes can be very thin)
    // REVERTED: Back to 2.5 per user feedback (3.0 was too thick)
    const MIN_STROKE_WIDTH: f32 = 2.5;
    // REVERTED: Back to 1.0 (no scaling)
    const STROKE_WIDTH_SCALE: f32 = 1.0;
    let mut strokes_rendered = 0usize;

    for shape_group in &shape_groups {
        // Sort shapes by z_order to ensure correct layering
        let mut shapes = shape_group.shapes().to_vec();
        shapes.sort_by(|a, b| a.z_order.cmp(&b.z_order));

        for shape in shapes {
            // Each shape has a points_id that tells us which PointsFile to look in
            if let Some(points_id) = &shape.points_id {
                // Find the matching PointsFile
                for pf in &points_files {
                    if pf.header().points_id == *points_id {
                        // Found the right file - now get the stroke data
                        if let Some(stroke) = pf.get_stroke(&shape.stroke_id) {
                            // Use the ACTUAL stroke width from shape data
                            // Apply scaling and minimum width for visibility
                            let actual_width = (shape.stroke_width * STROKE_WIDTH_SCALE).max(MIN_STROKE_WIDTH);
                            let stroke_style = StrokeStyle {
                                width: actual_width,
                                cap: raqote::LineCap::Round,
                                join: raqote::LineJoin::Round,
                                ..StrokeStyle::default()
                            };
                            // Render the stroke to the canvas
                            let _ = stroke.render(&mut draw_target, &draw_options, &stroke_style);
                            strokes_rendered += 1;
                        }
                        break; // Found the matching file, no need to check others
                    }
                }
            }
        }
    }

    if strokes_rendered == 0 {
        return Err(format!(
            "No renderable strokes found for page {} (shape_groups={}, points_files={})",
            page_id,
            shape_groups.len(),
            points_files.len()
        ));
    }

    // ========================================================================
    // STEP 4: Convert canvas to RGBA byte array
    // raqote uses ARGB internally, so we reorder to RGBA for Android
    // Preserve original colors (no forced black/white conversion)
    // ========================================================================
    let stride = width * 4;  // 4 bytes per pixel (RGBA)
    let data = draw_target.get_data();
    let mut buf = Vec::with_capacity((height * stride) as usize);

    for y in 0..height {
        for x in 0..width {
            let pixel = data[(y * width + x) as usize];
            // Extract ARGB and reorder to RGBA - preserve original colors
            let r = ((pixel >> 16) & 0xff) as u8;
            let g = ((pixel >> 8) & 0xff) as u8;
            let b = (pixel & 0xff) as u8;
            buf.push(r);
            buf.push(g);
            buf.push(b);
            buf.push(255);  // A - always fully opaque
        }
    }

    Ok((width, height, stride, buf))
}

// ============================================================================
// SECTION 3: UTILITY AND DEBUG FUNCTIONS
// ============================================================================

/// JNI function: Checks if a note exists on device.
///
/// Use this to verify a note has on-device data before attempting to render.
///
/// # Parameters (from Kotlin)
/// * `ksyncBase: String` - Base path to .ksync folder
/// * `uniqueId: String` - The note's uniqueId
///
/// # Returns
/// * JSON: `{"exists": bool, "document_exists": bool, "point_exists": bool, ...}`
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_noteExistsOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
    j_unique_id: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"exists\": false}").unwrap().into_raw(),
    };
    let unique_id: String = match env.get_string(&j_unique_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"exists\": false}").unwrap().into_raw(),
    };

    let document_path = PathBuf::from(&ksync_base).join("document").join(&unique_id);
    let point_path = PathBuf::from(&ksync_base).join("point").join(&unique_id);

    let doc_exists = document_path.exists();
    let point_exists = point_path.exists();

    let json = format!(
        "{{\"exists\": {}, \"document_exists\": {}, \"point_exists\": {}, \"document_path\": \"{}\", \"point_path\": \"{}\"}}",
        doc_exists || point_exists,
        doc_exists,
        point_exists,
        document_path.display(),
        point_path.display()
    );

    env.new_string(json).unwrap().into_raw()
}

/// Metadata extracted from NOTE_TREE Fleece database
#[derive(Debug, Clone, Default)]
struct NoteMetadataFromFleece {
    /// Note title
    title: Option<String>,
    /// Which marker/path produced the title
    title_source: Option<String>,
    /// Compact extraction warnings for debugging
    metadata_warnings: Vec<String>,
    /// Folder path (e.g., "/School/Math")
    location: Option<String>,
    /// Note type: 0=Library (handwritten), 1=Document (PDF annotation)
    note_type: Option<i32>,
    /// Favorite flag
    favorite: Option<bool>,
    /// Parent folder unique ID (for folder hierarchy)
    parent_unique_id: Option<String>,
}

/// JSON response structure for enumerated notes from filesystem.
#[derive(Serialize)]
struct FilesystemNoteInfo {
    /// The note's unique ID (folder name)
    unique_id: String,
    /// The note's title extracted from CouchBase Lite database
    title: Option<String>,
    /// Which native path produced the title
    title_source: Option<String>,
    /// Compact warnings about metadata extraction
    metadata_warnings: Vec<String>,
    /// Folder/location path (e.g., "/School/Math")
    location: Option<String>,
    /// Note type: 0=Library (handwritten), 1=Document (PDF annotation)
    note_type: Option<i32>,
    /// Whether this note is marked as favorite
    favorite: Option<bool>,
    /// Parent folder ID for hierarchy
    parent_unique_id: Option<String>,
    /// Text content extracted from HTML resource files
    text_content: Option<String>,
    /// Whether text content exists, even if the caller ignores the actual text
    has_text_content: bool,
    /// Whether document folder exists
    has_document: bool,
    /// Whether point folder exists
    has_points: bool,
    /// Number of shape files found
    shape_file_count: usize,
    /// Number of pages (from point subdirectories)
    page_count: usize,
    /// List of page IDs found
    page_ids: Vec<String>,
    /// Folder modification time (Unix timestamp in seconds) - use as creation date proxy
    modified_timestamp: u64,
}

/// JSON response structure for listing all notes on device.
#[derive(Serialize)]
struct AllNotesInfo {
    /// Total number of notes found
    total_notes: usize,
    /// Notes found in document folder
    document_notes: Vec<FilesystemNoteInfo>,
    /// Path that was scanned
    scanned_path: String,
    /// Debug info about title loading
    title_debug: String,
    /// Number of titles found
    titles_found: usize,
}

/// JNI function: Lists ALL notes on device by scanning .ksync filesystem.
///
/// This bypasses the content provider and directly scans the filesystem to find
/// all notes. This is essential because the content provider may not return
/// all note types (e.g., standalone notes vs PDF annotations).
///
/// # Parameters (from Kotlin)
/// * `ksyncBase: String` - Base path to .ksync folder (e.g., "/storage/emulated/0/.ksync")
///
/// # Returns
/// * JSON object with all notes found on device
///
/// # Example Response
/// ```json
/// {
///   "total_notes": 5,
///   "document_notes": [
///     {
///       "unique_id": "abc123...",
///       "has_document": true,
///       "has_points": true,
///       "shape_file_count": 3,
///       "page_count": 2,
///       "page_ids": ["page1", "page2"]
///     }
///   ],
///   "scanned_path": "/storage/emulated/0/.ksync"
/// }
/// ```
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_listAllNotesOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => {
            return env
                .new_string("{\"status\":\"error\",\"message\":\"invalid ksync_base\",\"error\":\"invalid ksync_base\"}")
                .unwrap()
                .into_raw()
        }
    };

    let result = list_all_notes_impl(&ksync_base);
    let json = match result {
        Ok(info) => serde_json::to_string_pretty(&info).unwrap_or_else(|_| "{}".into()),
        Err(e) => format!(
            "{{\"status\":\"error\",\"message\":{0:?},\"error\":{0:?}}}",
            e
        ),
    };

    env.new_string(json).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_getNoteTextContentOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
    j_unique_id: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let unique_id: String = match env.get_string(&j_unique_id) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let document_path = PathBuf::from(&ksync_base).join("document").join(&unique_id);
    let text_content = extract_text_content_from_resources(&document_path)
        .map(|text| text.trim().to_string())
        .filter(|text| !text.is_empty());

    match text_content {
        Some(text) => env
            .new_string(text)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

/// Implementation: Scans .ksync filesystem to find all notes.
///
/// # Algorithm
/// 1. Load note metadata from NOTE_TREE database (title, location, type, favorite)
/// 2. Scan .ksync/document/ for all subdirectories (each is a noteId)
/// 3. For each note, check what data exists (shapes, points, pages)
/// 4. Extract text content from HTML resource files
/// 5. Return comprehensive list of all notes found with full metadata
fn list_all_notes_impl(ksync_base: &str) -> Result<AllNotesInfo, String> {
    let base_path = PathBuf::from(ksync_base);
    let document_path = base_path.join("document");
    let point_base = base_path.join("point");

    if !document_path.exists() {
        return Err(format!("Document path does not exist: {}", document_path.display()));
    }

    // Load all note metadata from CouchBase Lite database
    let (metadata_map, title_debug) = load_note_metadata_with_debug(ksync_base);
    let titles_found = metadata_map.values().filter(|m| m.title.is_some()).count();

    let mut notes = Vec::new();

    // Scan all subdirectories in document/ - each one is a note
    let entries = fs::read_dir(&document_path)
        .map_err(|e| format!("Failed to read document directory: {}", e))?;

    for entry in entries.flatten() {
        if entry.path().is_dir() {
            let unique_id = entry.file_name().to_string_lossy().to_string();

            // Look up metadata from the database
            let metadata = metadata_map.get(&unique_id).cloned().unwrap_or_default();

            // Check for shape files
            let shape_dir = entry.path().join("shape");
            let shape_file_count = if shape_dir.exists() {
                fs::read_dir(&shape_dir)
                    .map(|entries| entries.flatten().count())
                    .unwrap_or(0)
            } else {
                0
            };

            // Check for point files and enumerate pages
            let point_path = point_base.join(&unique_id);
            let has_points = point_path.exists();
            let mut page_ids = Vec::new();

            if has_points {
                if let Ok(page_dirs) = fs::read_dir(&point_path) {
                    for page_dir in page_dirs.flatten() {
                        if page_dir.path().is_dir() {
                            page_ids.push(page_dir.file_name().to_string_lossy().to_string());
                        }
                    }
                }
            }

            // Also check shape files for page IDs (in case points don't exist)
            if page_ids.is_empty() && shape_dir.exists() {
                if let Ok(shape_files) = fs::read_dir(&shape_dir) {
                    for sf in shape_files.flatten() {
                        let name = sf.file_name().to_string_lossy().to_string();
                        if let Some(page_id) = name.split('#').next() {
                            if !page_ids.contains(&page_id.to_string()) {
                                page_ids.push(page_id.to_string());
                            }
                        }
                    }
                }
            }

            // Extract text content from HTML resource files
            let text_content = extract_text_content_from_resources(&entry.path());
            let has_text_content = text_content.is_some();

            // Get folder modification time as a proxy for note creation date
            let modified_timestamp = entry.metadata()
                .and_then(|m| m.modified())
                .ok()
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_secs())
                .unwrap_or(0);

            notes.push(FilesystemNoteInfo {
                unique_id,
                title: metadata.title,
                title_source: metadata.title_source,
                metadata_warnings: metadata.metadata_warnings,
                location: metadata.location,
                note_type: metadata.note_type,
                favorite: metadata.favorite,
                parent_unique_id: metadata.parent_unique_id,
                text_content,
                has_text_content,
                has_document: true,
                has_points,
                shape_file_count,
                page_count: page_ids.len(),
                page_ids,
                modified_timestamp,
            });
        }
    }

    // Sort by modification time (most recent first) for better UX
    notes.sort_by(|a, b| b.modified_timestamp.cmp(&a.modified_timestamp));

    Ok(AllNotesInfo {
        total_notes: notes.len(),
        document_notes: notes,
        scanned_path: ksync_base.to_string(),
        title_debug,
        titles_found,
    })
}

/// JNI function: Debug render - returns detailed info about parsing.
///
/// Use this for troubleshooting rendering issues. Returns information about:
/// - What files were found
/// - Whether they parsed successfully
/// - UUID matching between shapes and points
/// - How many strokes were rendered
///
/// # Parameters (from Kotlin)
/// * `ksyncBase: String` - Base path to .ksync folder
/// * `uniqueId: String` - The note's uniqueId
/// * `pageId: String` - The page ID
///
/// # Returns
/// * Detailed JSON with debug information
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_debugRenderOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
    j_unique_id: JString,
    j_page_id: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"error\": \"invalid ksync_base\"}").unwrap().into_raw(),
    };
    let unique_id: String = match env.get_string(&j_unique_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"error\": \"invalid unique_id\"}").unwrap().into_raw(),
    };
    let page_id: String = match env.get_string(&j_page_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"error\": \"invalid page_id\"}").unwrap().into_raw(),
    };

    let result = debug_render_impl(&ksync_base, &unique_id, &page_id);
    env.new_string(result).unwrap().into_raw()
}

/// Debug information about the rendering process.
#[derive(Serialize)]
struct DebugRenderInfo {
    /// Whether the shape directory exists
    shape_dir_exists: bool,
    /// List of shape files found in the directory
    shape_files_found: Vec<String>,
    /// Number of shape groups successfully parsed
    shape_groups_parsed: usize,
    /// Number of shapes that have a points_id
    shapes_with_points_id: usize,
    /// Total number of shapes found
    total_shapes: usize,
    /// Whether the point directory exists
    point_dir_exists: bool,
    /// List of point files found
    point_files_found: Vec<String>,
    /// Number of point files successfully parsed
    points_files_parsed: usize,
    /// List of points_ids found in parsed files
    points_ids: Vec<String>,
    /// Details of each UUID matching attempt
    matching_attempts: Vec<DebugMatchAttempt>,
    /// Total strokes successfully rendered
    strokes_rendered: usize,
    /// Any errors encountered during parsing
    errors: Vec<String>,
}

/// Debug information about a single shape-to-points matching attempt.
#[derive(Serialize)]
struct DebugMatchAttempt {
    /// The stroke_id from the shape
    shape_stroke_id: String,
    /// The points_id from the shape
    shape_points_id: String,
    /// The points_id from the PointsFile header
    points_file_points_id: String,
    /// Whether the IDs matched
    ids_match: bool,
    /// Whether the stroke was found in the PointsFile
    stroke_found: bool,
    /// Number of points in the stroke (if found)
    point_count: usize,
}

/// Implementation: Collects debug information about the rendering process.
fn debug_render_impl(ksync_base: &str, unique_id: &str, page_id: &str) -> String {
    let mut info = DebugRenderInfo {
        shape_dir_exists: false,
        shape_files_found: Vec::new(),
        shape_groups_parsed: 0,
        shapes_with_points_id: 0,
        total_shapes: 0,
        point_dir_exists: false,
        point_files_found: Vec::new(),
        points_files_parsed: 0,
        points_ids: Vec::new(),
        matching_attempts: Vec::new(),
        strokes_rendered: 0,
        errors: Vec::new(),
    };

    let document_path = PathBuf::from(ksync_base).join("document").join(unique_id);
    let point_path = PathBuf::from(ksync_base).join("point").join(unique_id);

    // Load and analyze shape files
    let mut shape_groups: Vec<ShapeGroup> = Vec::new();
    let shape_dir = document_path.join("shape");
    info.shape_dir_exists = shape_dir.exists();

    if shape_dir.exists() {
        if let Ok(entries) = fs::read_dir(&shape_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                info.shape_files_found.push(name.clone());

                if name.starts_with(page_id) && name.ends_with(".zip") {
                    if let Ok(mut file) = File::open(entry.path()) {
                        let mut buf = Vec::new();
                        if file.read_to_end(&mut buf).is_ok() {
                            match ShapeGroup::read(Cursor::new(buf)) {
                                Ok(sg) => {
                                    info.shape_groups_parsed += 1;
                                    info.total_shapes += sg.shapes().len();
                                    for shape in sg.shapes() {
                                        if shape.points_id.is_some() {
                                            info.shapes_with_points_id += 1;
                                        }
                                    }
                                    shape_groups.push(sg);
                                }
                                Err(e) => {
                                    info.errors.push(format!("Shape parse error: {:?}", e));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Load and analyze points files
    let mut points_files: Vec<PointsFile> = Vec::new();
    let page_point_dir = point_path.join(page_id);
    info.point_dir_exists = page_point_dir.exists();

    if page_point_dir.exists() {
        if let Ok(entries) = fs::read_dir(&page_point_dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                info.point_files_found.push(name.clone());

                if name.ends_with("#points") {
                    if let Ok(mut file) = File::open(entry.path()) {
                        let mut buf = Vec::new();
                        if file.read_to_end(&mut buf).is_ok() {
                            match PointsFile::read(Cursor::new(buf)) {
                                Ok(pf) => {
                                    info.points_files_parsed += 1;
                                    info.points_ids.push(format!("{:?}", pf.header().points_id));
                                    points_files.push(pf);
                                }
                                Err(e) => {
                                    info.errors.push(format!("Points parse error: {:?}", e));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Try matching shapes to points and collect debug info
    for shape_group in &shape_groups {
        for shape in shape_group.shapes() {
            if let Some(points_id) = &shape.points_id {
                for pf in &points_files {
                    let ids_match = pf.header().points_id == *points_id;
                    let stroke_found = if ids_match {
                        pf.get_stroke(&shape.stroke_id).is_some()
                    } else {
                        false
                    };
                    let point_count = if stroke_found {
                        pf.get_stroke(&shape.stroke_id).map(|s| s.points.len()).unwrap_or(0)
                    } else {
                        0
                    };

                    info.matching_attempts.push(DebugMatchAttempt {
                        shape_stroke_id: format!("{:?}", shape.stroke_id),
                        shape_points_id: format!("{:?}", points_id),
                        points_file_points_id: format!("{:?}", pf.header().points_id),
                        ids_match,
                        stroke_found,
                        point_count,
                    });

                    if stroke_found {
                        info.strokes_rendered += 1;
                    }
                }
            }
        }
    }

    serde_json::to_string_pretty(&info).unwrap_or_else(|e| format!("{{\"error\": \"{}\"}}", e))
}

/// JNI function: Get detailed stroke data from a page.
///
/// This function is for advanced use cases where you need raw stroke data
/// rather than a rendered image.
///
/// NOTE: Currently returns limited data. Would need to expose more from
/// PointsFile to get complete stroke information.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_getStrokeDataOnDevice(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
    j_unique_id: JString,
    j_page_id: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let unique_id: String = match env.get_string(&j_unique_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let page_id: String = match env.get_string(&j_page_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let result = get_stroke_data_impl(&ksync_base, &unique_id, &page_id);
    let json = match result {
        Ok(data) => data,
        Err(e) => format!("{{\"error\": \"{}\"}}", e),
    };

    env.new_string(json).unwrap().into_raw()
}

/// Stroke data for JSON serialization.
#[derive(Serialize)]
struct StrokeData {
    stroke_id: String,
    point_count: usize,
    bbox: StrokeBbox,
}

/// Bounding box for a stroke.
#[derive(Serialize)]
struct StrokeBbox {
    min_x: f32,
    max_x: f32,
    min_y: f32,
    max_y: f32,
}

/// Page stroke data response.
#[derive(Serialize)]
struct PageStrokeData {
    page_id: String,
    stroke_count: usize,
    strokes: Vec<StrokeData>,
}

/// Implementation: Collects stroke data from point files.
fn get_stroke_data_impl(
    ksync_base: &str,
    unique_id: &str,
    page_id: &str,
) -> Result<String, String> {
    let point_path = PathBuf::from(ksync_base).join("point").join(unique_id);
    let page_point_dir = point_path.join(page_id);

    if !page_point_dir.exists() {
        return Err(format!("Point directory not found: {}", page_point_dir.display()));
    }

    let all_strokes: Vec<StrokeData> = Vec::new();

    // NOTE: To fully implement this, we'd need to expose stroke iteration
    // from PointsFile. Currently just returns empty strokes array.
    if let Ok(entries) = fs::read_dir(&page_point_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.ends_with("#points") {
                if let Ok(mut file) = File::open(entry.path()) {
                    let mut buf = Vec::new();
                    if file.read_to_end(&mut buf).is_ok() {
                        if let Ok(_pf) = PointsFile::read(Cursor::new(buf)) {
                            // TODO: Iterate strokes and collect data
                            // Would need to expose stroke iteration from PointsFile
                        }
                    }
                }
            }
        }
    }

    let page_data = PageStrokeData {
        page_id: page_id.to_string(),
        stroke_count: all_strokes.len(),
        strokes: all_strokes,
    };

    serde_json::to_string(&page_data).map_err(|e| e.to_string())
}

// ============================================================================
// SECTION 4: COUCHBASE LITE TITLE EXTRACTION
// ============================================================================
// These functions extract note titles from CouchBase Lite 2.x databases.
// The NOTE_TREE database stores note metadata including titles in Fleece format.
// ============================================================================

/// Extract a string value from Fleece binary data after a specific marker.
///
/// Fleece format for strings:
/// - 0x40-0x4e: Short string, length encoded in low nibble (0-14 chars)
/// - 0x4f followed by varint: Longer string with length in following bytes
///
/// # Arguments
/// * `body` - The Fleece binary data
/// * `marker` - The marker bytes to search for (e.g., b"ELocal" for title)
fn extract_string_after_marker(body: &[u8], marker: &[u8]) -> Option<String> {
    // Find the marker in the body
    let mut pos = None;
    for i in 0..body.len().saturating_sub(marker.len()) {
        if &body[i..i + marker.len()] == marker {
            pos = Some(i + marker.len());
            break;
        }
    }

    let pos = pos?;
    if pos >= body.len() {
        return None;
    }

    // The byte at pos indicates the string type and length
    let type_byte = body[pos];

    // Fleece short strings: 0x40-0x4f, length = type_byte & 0x0f
    if type_byte >= 0x40 && type_byte <= 0x4f {
        let length = (type_byte & 0x0f) as usize;

        // Check for extended length (type_byte == 0x4f means length follows)
        if type_byte == 0x4f && pos + 1 < body.len() {
            // Next byte(s) contain the actual length
            let ext_length = body[pos + 1] as usize;
            if pos + 2 + ext_length <= body.len() {
                let str_bytes = &body[pos + 2..pos + 2 + ext_length];
                if let Ok(s) = std::str::from_utf8(str_bytes) {
                    let cleaned: String = s.trim().to_string();
                    if !cleaned.is_empty() {
                        return Some(cleaned);
                    }
                }
            }
        } else if length > 0 && pos + 1 + length <= body.len() {
            // Short string: length encoded in type byte
            let str_bytes = &body[pos + 1..pos + 1 + length];
            if let Ok(s) = std::str::from_utf8(str_bytes) {
                let cleaned: String = s.trim().to_string();
                if !cleaned.is_empty() {
                    return Some(cleaned);
                }
            }
        }
    }

    None
}

/// SharedKey indices from CouchBase Lite NOTE_TREE database.
/// These are fixed indices for the Fleece dictionary structure.
const SHARED_KEY_FAVORITE: u8 = 25;
const SHARED_KEY_TYPE: u8 = 38;
const SHARED_KEY_PARENT_UNIQUE_ID: u8 = 83;

/// Extract integer value for a SharedKey from Fleece dictionary structure.
///
/// The Fleece dictionary at the end of records uses a structure where:
/// - Key indices appear followed by their values
/// - Pattern: [key_byte, 0, value_low, value_high_or_next_key, ...]
///
/// # Arguments
/// * `body` - The Fleece binary data
/// * `key_index` - The SharedKey index to find
fn extract_shared_key_int(body: &[u8], key_index: u8) -> Option<i32> {
    // Search in the last 200 bytes where dictionary structure lives
    let search_start = if body.len() > 200 { body.len() - 200 } else { 0 };
    let search_area = &body[search_start..];

    for i in 0..search_area.len().saturating_sub(4) {
        if search_area[i] == key_index {
            // Found the key index, value is at offset +2 based on observed pattern
            // Pattern: [key, 0, value_low, value_high_or_padding]
            let value = search_area[i + 2] as i32;
            // Validate: value should be small for type/favorite (0-7 typically)
            if value <= 7 {
                return Some(value);
            }
        }
    }
    None
}

/// Extract boolean value for a SharedKey from Fleece dictionary.
fn extract_shared_key_bool(body: &[u8], key_index: u8) -> Option<bool> {
    extract_shared_key_int(body, key_index).map(|v| v != 0)
}

/// Extract an integer value from Fleece binary data after a specific marker.
///
/// Fleece format for small integers:
/// - 0x00-0x07: Small positive integers (0-7), value is the byte itself
/// - 0x08-0x0f: Small negative integers
/// - 0x10-0x17: Larger integers with additional bytes
fn extract_int_after_marker(body: &[u8], marker: &[u8]) -> Option<i32> {
    // Find the marker in the body
    let mut pos = None;
    for i in 0..body.len().saturating_sub(marker.len()) {
        if &body[i..i + marker.len()] == marker {
            pos = Some(i + marker.len());
            break;
        }
    }

    let pos = pos?;
    if pos >= body.len() {
        return None;
    }

    let type_byte = body[pos];

    // Fleece small integers: 0x00-0x0f represent values 0-7 directly (low 3 bits)
    // Higher bytes indicate larger integer encodings
    if type_byte <= 0x07 {
        return Some(type_byte as i32);
    }

    // For integers 0x10-0x17, additional bytes follow
    if type_byte >= 0x10 && type_byte <= 0x17 {
        let extra_bytes = (type_byte & 0x07) as usize;
        if pos + 1 + extra_bytes <= body.len() {
            let mut value: i32 = 0;
            for i in 0..extra_bytes {
                value |= (body[pos + 1 + i] as i32) << (i * 8);
            }
            return Some(value);
        }
    }

    None
}

/// Extract a boolean value from Fleece binary data after a specific marker.
///
/// Fleece booleans:
/// - 0x00 or specific false marker = false
/// - 0x01 or specific true marker = true
fn extract_bool_after_marker(body: &[u8], marker: &[u8]) -> Option<bool> {
    // Find the marker in the body
    let mut pos = None;
    for i in 0..body.len().saturating_sub(marker.len()) {
        if &body[i..i + marker.len()] == marker {
            pos = Some(i + marker.len());
            break;
        }
    }

    let pos = pos?;
    if pos >= body.len() {
        return None;
    }

    let type_byte = body[pos];

    // In Fleece: 0x00 = false, 0x01 = true (for small integers used as booleans)
    // Also check for special constants
    match type_byte {
        0x00 => Some(false),
        0x01 => Some(true),
        0x3e => Some(false), // Fleece false constant
        0x3f => Some(true),  // Fleece true constant
        _ => None,
    }
}

fn looks_like_uuid(value: &str) -> bool {
    let normalized = value.replace('-', "");
    normalized.len() == 32 && normalized.chars().all(|c| c.is_ascii_hexdigit())
}

fn sanitize_title_candidate(
    raw: Option<String>,
    source: &str,
    warnings: &mut Vec<String>,
) -> Option<String> {
    let cleaned = raw
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())?;

    let without_controls: String = cleaned.chars().filter(|c| !c.is_control()).collect();
    let candidate = without_controls.trim().to_string();

    if candidate.is_empty() {
        warnings.push(format!("Rejected {} title candidate: empty after cleanup", source));
        return None;
    }
    if candidate.len() > 120 {
        warnings.push(format!("Rejected {} title candidate: too long", source));
        return None;
    }
    if candidate.eq_ignore_ascii_case("local") || candidate.eq_ignore_ascii_case("title") {
        warnings.push(format!("Rejected {} title candidate: placeholder string", source));
        return None;
    }
    if looks_like_uuid(&candidate) {
        warnings.push(format!("Rejected {} title candidate: uuid-like", source));
        return None;
    }
    if candidate.contains(".zip")
        || candidate.contains("/storage/")
        || candidate.contains("document/")
        || candidate.starts_with('{')
        || candidate.starts_with('[')
    {
        warnings.push(format!("Rejected {} title candidate: looks like metadata", source));
        return None;
    }
    if candidate.chars().filter(|c| c.is_alphanumeric()).count() < 2 {
        warnings.push(format!("Rejected {} title candidate: not enough text", source));
        return None;
    }

    Some(candidate)
}

/// Extract all metadata fields from Fleece binary data.
///
/// This function extracts title candidates from known markers and picks the
/// safest one instead of trusting the first byte-pattern hit.
///
/// Note: Type and Favorite extraction via SharedKey indices is DISABLED because the simple
/// byte-matching approach causes too many false positives. The Fleece dictionary structure
/// requires proper parsing to reliably extract these values. Returning None lets the UI
/// default to type=0 (Library note), which allows all notes to pass the filter.
///
/// Note: "location" field does NOT exist in the database - folders use parentUniqueId instead.
fn extract_metadata_from_fleece(body: &[u8]) -> NoteMetadataFromFleece {
    let mut metadata = NoteMetadataFromFleece::default();
    let mut warnings = Vec::new();

    let title_candidate = sanitize_title_candidate(
        extract_string_after_marker(body, b"Etitle"),
        "Etitle",
        &mut warnings,
    );
    let local_candidate = sanitize_title_candidate(
        extract_string_after_marker(body, b"ELocal"),
        "ELocal",
        &mut warnings,
    );

    match (title_candidate, local_candidate) {
        (Some(title), Some(local)) if title == local => {
            metadata.title = Some(title);
            metadata.title_source = Some("Etitle".to_string());
        }
        (Some(title), Some(local)) => {
            metadata.title = Some(title);
            metadata.title_source = Some("Etitle".to_string());
            warnings.push(format!("ELocal disagreed with Etitle: {}", local));
        }
        (Some(title), None) => {
            metadata.title = Some(title);
            metadata.title_source = Some("Etitle".to_string());
        }
        (None, Some(local)) => {
            metadata.title = Some(local);
            metadata.title_source = Some("ELocal".to_string());
            warnings.push("Fell back to ELocal title heuristic".to_string());
        }
        (None, None) => {
            warnings.push("No trusted title marker found".to_string());
        }
    }

    // Location field does NOT exist in NOTE_TREE database
    metadata.location = None;

    // DISABLED: SharedKey-based extraction causes false positives.
    // Byte values 38/25 can appear anywhere in data, not just as dictionary keys.
    // This caused MainActivity's `it.type == 0` filter to exclude valid notes.
    // Proper extraction requires full Fleece dictionary parsing.
    metadata.note_type = None;
    metadata.favorite = None;
    metadata.parent_unique_id = None;
    metadata.metadata_warnings = warnings;

    metadata
}

/// Extract a string from Fleece binary data after a specific marker.
/// Legacy function for backwards compatibility.
fn extract_title_from_fleece(body: &[u8]) -> Option<String> {
    let mut warnings = Vec::new();
    sanitize_title_candidate(
        extract_string_after_marker(body, b"Etitle"),
        "Etitle",
        &mut warnings,
    )
        .or_else(|| {
            sanitize_title_candidate(
                extract_string_after_marker(body, b"ELocal"),
                "ELocal",
                &mut warnings,
            )
        })
}

/// Load all note metadata from the NOTE_TREE CouchBase Lite database.
///
/// # Arguments
/// * `ksync_base` - Base path to .ksync folder
///
/// # Returns
/// * (HashMap mapping note_id -> NoteMetadataFromFleece, debug string)
fn load_note_metadata_with_debug(ksync_base: &str) -> (HashMap<String, NoteMetadataFromFleece>, String) {
    let mut debug_info = Vec::new();
    let mut metadata_map: HashMap<String, NoteMetadataFromFleece> = HashMap::new();

    // Find the NOTE_TREE database
    let couch_path = PathBuf::from(ksync_base).join("couch");
    if !couch_path.exists() {
        debug_info.push(format!("couch path does not exist: {:?}", couch_path));
        return (metadata_map, debug_info.join("; "));
    }
    debug_info.push(format!("couch path exists: {:?}", couch_path));

    // Look for *-NOTE_TREE.cblite2 directory
    let entries = match fs::read_dir(&couch_path) {
        Ok(e) => e,
        Err(e) => {
            debug_info.push(format!("failed to read couch dir: {}", e));
            return (metadata_map, debug_info.join("; "));
        }
    };

    let mut note_tree_db: Option<PathBuf> = None;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        // Skip share_user- prefixed databases, we want the user's actual database
        if name.ends_with("-NOTE_TREE.cblite2") && !name.starts_with("share_user-") && entry.path().is_dir() {
            note_tree_db = Some(entry.path().join("db.sqlite3"));
            debug_info.push(format!("found NOTE_TREE: {}", name));
            break;
        }
    }

    let db_path = match note_tree_db {
        Some(p) if p.exists() => {
            debug_info.push(format!("db.sqlite3 exists at {:?}", p));
            p
        }
        Some(p) => {
            debug_info.push(format!("db.sqlite3 does not exist at {:?}", p));
            return (metadata_map, debug_info.join("; "));
        }
        None => {
            debug_info.push("no NOTE_TREE.cblite2 directory found".to_string());
            return (metadata_map, debug_info.join("; "));
        }
    };

    // Open the SQLite database
    let conn = match Connection::open(&db_path) {
        Ok(c) => c,
        Err(e) => {
            debug_info.push(format!("failed to open db: {}", e));
            return (metadata_map, debug_info.join("; "));
        }
    };
    debug_info.push("db opened successfully".to_string());

    // Query all entries from kv_default
    let mut stmt = match conn.prepare("SELECT key, body FROM kv_default") {
        Ok(s) => s,
        Err(e) => {
            debug_info.push(format!("failed to prepare query: {}", e));
            return (metadata_map, debug_info.join("; "));
        }
    };

    let rows = match stmt.query_map([], |row| {
        let key: String = row.get(0)?;
        let body: Vec<u8> = row.get(1)?;
        Ok((key, body))
    }) {
        Ok(r) => r,
        Err(e) => {
            debug_info.push(format!("query failed: {}", e));
            return (metadata_map, debug_info.join("; "));
        }
    };

    let mut row_count = 0;
    let mut metadata_count = 0;
    for row_result in rows {
        if let Ok((key, body)) = row_result {
            row_count += 1;
            let metadata = extract_metadata_from_fleece(&body);
            // Only count if we extracted at least a title
            if metadata.title.is_some() {
                metadata_count += 1;
            }
            metadata_map.insert(key, metadata);
        }
    }
    debug_info.push(format!("processed {} rows, found {} with metadata", row_count, metadata_count));

    (metadata_map, debug_info.join("; "))
}

/// Legacy function - Load note titles from the NOTE_TREE CouchBase Lite database with debug info.
fn load_note_titles_with_debug(ksync_base: &str) -> (HashMap<String, String>, String) {
    let (metadata_map, debug) = load_note_metadata_with_debug(ksync_base);
    let titles: HashMap<String, String> = metadata_map
        .into_iter()
        .filter_map(|(k, v)| v.title.map(|t| (k, t)))
        .collect();
    (titles, debug)
}

/// Extract text content from HTML resource files in a note's resource/data folder.
///
/// Boox stores typed text in ProseMirror JSON format inside HTML files.
/// This function reads all HTML files and extracts the plain text content.
///
/// # Arguments
/// * `document_path` - Path to the note's document folder (e.g., .ksync/document/{noteId})
///
/// # Returns
/// * Combined text content from all HTML files, or None if no text found
fn extract_text_content_from_resources(document_path: &PathBuf) -> Option<String> {
    let resource_data_path = document_path.join("resource").join("data");

    if !resource_data_path.exists() {
        return None;
    }

    let mut all_text = Vec::new();

    if let Ok(entries) = fs::read_dir(&resource_data_path) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().map(|e| e == "html").unwrap_or(false) {
                if let Ok(content) = fs::read_to_string(&path) {
                    // Try to parse as JSON and extract text content
                    if let Some(text) = extract_text_from_prosemirror_json(&content) {
                        if !text.is_empty() {
                            all_text.push(text);
                        }
                    }
                }
            }
        }
    }

    if all_text.is_empty() {
        None
    } else {
        Some(all_text.join("\n"))
    }
}

/// Extract plain text from ProseMirror JSON content.
///
/// ProseMirror format example:
/// ```json
/// {
///   "content": {
///     "type": "doc",
///     "content": [
///       { "type": "paragraph", "content": [{ "type": "text", "text": "Hello" }] }
///     ]
///   }
/// }
/// ```
fn extract_text_from_prosemirror_json(json_str: &str) -> Option<String> {
    // Try to parse as JSON
    let value: serde_json::Value = serde_json::from_str(json_str).ok()?;

    let mut text_parts = Vec::new();
    let root = value.get("content").unwrap_or(&value);
    extract_text_recursive(root, &mut text_parts);

    if text_parts.is_empty() {
        None
    } else {
        Some(text_parts.join(" "))
    }
}

/// Recursively extract text from ProseMirror JSON nodes.
fn extract_text_recursive(value: &serde_json::Value, text_parts: &mut Vec<String>) {
    match value {
        serde_json::Value::Object(obj) => {
            // If this node has a "text" field, extract it
            if let Some(serde_json::Value::String(text)) = obj.get("text") {
                if !text.trim().is_empty() {
                    text_parts.push(text.clone());
                }
            }
            // Recurse into "content" array if present
            if let Some(content) = obj.get("content") {
                extract_text_recursive(content, text_parts);
            }
        }
        serde_json::Value::Array(arr) => {
            for item in arr {
                extract_text_recursive(item, text_parts);
            }
        }
        _ => {}
    }
}

/// Load note titles from the NOTE_TREE CouchBase Lite database.
///
/// # Arguments
/// * `ksync_base` - Base path to .ksync folder
///
/// # Returns
/// * HashMap mapping note_id -> title
#[allow(dead_code)]
fn load_note_titles(ksync_base: &str) -> HashMap<String, String> {
    let mut titles: HashMap<String, String> = HashMap::new();

    // Find the NOTE_TREE database
    let couch_path = PathBuf::from(ksync_base).join("couch");
    if !couch_path.exists() {
        eprintln!("load_note_titles: couch path does not exist: {:?}", couch_path);
        return titles;
    }

    // Look for *-NOTE_TREE.cblite2 directory
    let entries = match fs::read_dir(&couch_path) {
        Ok(e) => e,
        Err(e) => {
            eprintln!("load_note_titles: failed to read couch dir: {}", e);
            return titles;
        }
    };

    let mut note_tree_db: Option<PathBuf> = None;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        if name.ends_with("-NOTE_TREE.cblite2") && entry.path().is_dir() {
            note_tree_db = Some(entry.path().join("db.sqlite3"));
            eprintln!("load_note_titles: found NOTE_TREE db at {:?}", note_tree_db);
            break;
        }
    }

    let db_path = match note_tree_db {
        Some(p) if p.exists() => p,
        Some(p) => {
            eprintln!("load_note_titles: db.sqlite3 does not exist at {:?}", p);
            return titles;
        }
        None => {
            eprintln!("load_note_titles: no NOTE_TREE.cblite2 directory found");
            return titles;
        }
    };

    // Open the SQLite database
    let conn = match Connection::open(&db_path) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("load_note_titles: failed to open db: {}", e);
            return titles;
        }
    };

    // Query all entries from kv_default
    let mut stmt = match conn.prepare("SELECT key, body FROM kv_default") {
        Ok(s) => s,
        Err(e) => {
            eprintln!("load_note_titles: failed to prepare query: {}", e);
            return titles;
        }
    };

    let rows = match stmt.query_map([], |row| {
        let key: String = row.get(0)?;
        let body: Vec<u8> = row.get(1)?;
        Ok((key, body))
    }) {
        Ok(r) => r,
        Err(e) => {
            eprintln!("load_note_titles: query failed: {}", e);
            return titles;
        }
    };

    let mut row_count = 0;
    for row_result in rows {
        if let Ok((key, body)) = row_result {
            row_count += 1;
            if let Some(title) = extract_title_from_fleece(&body) {
                eprintln!("load_note_titles: found title '{}' for key '{}'", title, key);
                titles.insert(key, title);
            }
        }
    }
    eprintln!("load_note_titles: processed {} rows, found {} titles", row_count, titles.len());

    titles
}

/// JNI function: Get all note titles from the NOTE_TREE database.
///
/// # Parameters (from Kotlin)
/// * `ksyncBase: String` - Base path to .ksync folder
///
/// # Returns
/// * JSON object mapping note_id -> title
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boox_day_booxnotes_BooxNative_getNoteTitles(
    mut env: JNIEnv,
    _class: JClass,
    j_ksync_base: JString,
) -> jstring {
    let ksync_base: String = match env.get_string(&j_ksync_base) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let titles = load_note_titles(&ksync_base);
    let json = serde_json::to_string(&titles).unwrap_or_else(|_| "{}".into());

    env.new_string(json).unwrap().into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fleece_string_with_marker(marker: &[u8], value: &str) -> Vec<u8> {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(b"prefix");
        bytes.extend_from_slice(marker);
        if value.len() < 0x0f {
            bytes.push(0x40 | value.len() as u8);
        } else {
            bytes.push(0x4f);
            bytes.push(value.len() as u8);
        }
        bytes.extend_from_slice(value.as_bytes());
        bytes.extend_from_slice(b"suffix");
        bytes
    }

    #[test]
    fn sanitize_title_candidate_rejects_uuid_like_values() {
        let mut warnings = Vec::new();
        let result = sanitize_title_candidate(
            Some("0342de42e74b44bca8dc37c2422d5475".to_string()),
            "Etitle",
            &mut warnings,
        );

        assert_eq!(result, None);
        assert!(warnings.iter().any(|warning| warning.contains("uuid-like")));
    }

    #[test]
    fn extract_metadata_prefers_etitle_when_elocal_disagrees() {
        let mut body = fleece_string_with_marker(b"Etitle", "Project Sync");
        body.extend(fleece_string_with_marker(b"ELocal", "Apr 24"));

        let metadata = extract_metadata_from_fleece(&body);

        assert_eq!(metadata.title.as_deref(), Some("Project Sync"));
        assert_eq!(metadata.title_source.as_deref(), Some("Etitle"));
        assert!(metadata.metadata_warnings.iter().any(|warning| warning.contains("disagreed")));
    }

    #[test]
    fn extract_metadata_falls_back_to_elocal_when_etitle_is_bad() {
        let mut body = fleece_string_with_marker(b"Etitle", "0342de42e74b44bca8dc37c2422d5475");
        body.extend(fleece_string_with_marker(b"ELocal", "Meeting Notes"));

        let metadata = extract_metadata_from_fleece(&body);

        assert_eq!(metadata.title.as_deref(), Some("Meeting Notes"));
        assert_eq!(metadata.title_source.as_deref(), Some("ELocal"));
        assert!(metadata.metadata_warnings.iter().any(|warning| warning.contains("uuid-like")));
        assert!(metadata
            .metadata_warnings
            .iter()
            .any(|warning| warning.contains("Fell back to ELocal")));
    }

    #[test]
    fn extract_text_from_prosemirror_json_collects_nested_text() {
        let json = r#"{
            "content": {
                "type": "doc",
                "content": [
                    { "type": "paragraph", "content": [{ "type": "text", "text": "Hello" }] },
                    { "type": "paragraph", "content": [{ "type": "text", "text": "Boox" }] }
                ]
            }
        }"#;

        let text = extract_text_from_prosemirror_json(json);

        assert_eq!(text.as_deref(), Some("Hello Boox"));
    }
}
