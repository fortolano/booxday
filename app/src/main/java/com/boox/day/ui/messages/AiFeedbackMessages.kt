package com.boox.day.ui.messages

import kotlin.random.Random

object AiFeedbackMessages {

    // Shuffle queues for ALL message types - ensures no repeats until all messages shown once
    // Uses "shuffle bag" pattern: shuffle deck, draw until empty, reshuffle
    private val audioMessageQueue = mutableListOf<String>()
    private val ocrMessageQueue = mutableListOf<String>()
    private val breakdownMessageQueue = mutableListOf<String>()
    private val prioritizationMessageQueue = mutableListOf<String>()
    private val memoToTasksMessageQueue = mutableListOf<String>()
    private val suggestionsMessageQueue = mutableListOf<String>()
    private val planningMessageQueue = mutableListOf<String>()
    private val generalMessageQueue = mutableListOf<String>()
    private val syncingMessageQueue = mutableListOf<String>()
    private val syncCompleteMessageQueue = mutableListOf<String>()
    private val notesMatchingMessageQueue = mutableListOf<String>()
    private val summarizationMessageQueue = mutableListOf<String>()
    private val translationMessageQueue = mutableListOf<String>()

    private val audioMessages = listOf(
        "Pretending I understood all of that...",
        "Translating human sounds. Fascinating species...",
        "Your words are tickling my microphone...",
        "Your voice just entered my brain. Processing...",
        "Frankly my dear, I didn't hear a thing... just kidding!",
        "You had me at hello. I think. Say it again? (just kidding!)",
        "Houston, we have audio...",
        "I'm gonna make you a transcript you can't refuse...",
        "May the frequency be with you...",
        "You're gonna need a bigger microphone...",
        "I feel the need... the need to transcribe...",
        "Who you gonna call? Transcribers!",
        "Where we're going, we don't need subtitles...",
        "Transcription is like onions. It has layers...",
        "Wakanda audio is this?!",
        "With great audio comes great transcription...",
        "I have a very particular set of audio skills..."
    )

    private val ocrMessages = listOf(
        // The Matrix
        "I know kung fu... and cursive, apparently...",
        "There is no spoon... but I see your pen strokes...",
        // Lord of the Rings
        "One does not simply read this handwriting...",
        // Harry Potter
        "I solemnly swear your handwriting is up to no good...",
        // Star Wars
        "These are the scribbles I'm looking for...",
        "The handwriting is strong with this one...",
        "Help me OCR-Wan, you're my only hope...",
        "I find your lack of legibility disturbing...",
        "It's a cursive trap!",
        "Read or read not, there is no guess...",
        "May the font be with you...",
        "I've got a bad feeling about this handwriting...",
        // Sherlock Holmes
        "Elementary, my dear... wait, what does that say?",
        // Terminator
        "I'll be back... after I decode this...",
        // Jerry Maguire
        "Show me the letters!",
        // A Few Good Men
        "You can't handle the cursive!",
        // The Godfather
        "Leave the pen, take the meaning...",
        // Toy Story
        "To legibility and beyond!",
        // Ghostbusters
        "I ain't afraid of no cursive!",
        // Apollo 13
        "Houston, we have handwriting...",
        // Taken
        "I will find the meaning. I will decode it...",
        // Shrek
        "Better out than in... wait, is that an 'e' or an 'o'?",
        // Frozen
        "Let it flow, let it flow... your handwriting, that is",
        // The Wizard of Oz
        "Toto, I don't think that's English anymore..."
    )

    private val breakdownMessages = listOf(
        // Terminator
        "Hasta la vista, big task...",
        "I'll be back... with smaller tasks...",
        // Jaws
        "You're gonna need a smaller task...",
        // MC Hammer
        "Stop! Subtask time...",
        // Jerry Maguire
        "Show me the subtasks!",
        "You had me at 'break it down'...",
        // Lord of the Rings
        "One does not simply tackle this whole task...",
        // Toy Story
        "To subtasks... and beyond!",
        // Europe
        "It's the final breakdown...",
        // Avengers
        "I can break this down all day...",
        // Queen
        "We will... we will... break you down!",
        "Another one breaks the task...",
        // A Few Good Men
        "You can't handle the whole task!",
        // Star Wars
        "May the subtasks be with you...",
        // The Matrix
        "There is no big task... only subtasks...",
        // Ghostbusters
        "Who you gonna call? Task breakers!",
        // Michael Jackson
        "Just break it, break it...",
        // Taken
        "I have a very particular set of breakdown skills...",
        // Black Panther
        "Wakanda breakdown is this?!",
        // Bobby McFerrin
        "Don't worry, be happy... it's just subtasks now..."
    )

    private val prioritizationMessages = listOf(
        // Harry Potter
        "Sorting hat says... this task first!",
        "You need to sort out your priorities, Mr. Potter...",
        // Lord of the Rings
        "One priority to rule them all...",
        // Highlander
        "There can only be one... at the top!",
        // Queen
        "We are the champions... of priority!",
        "Another one ranks the list...",
        // Star Wars
        "I find your lack of ranking disturbing...",
        "These are the priorities you're looking for...",
        // Jerry Maguire
        "Show me the priorities!",
        // MC Hammer
        "Stop! Priority time...",
        // Tina Turner
        "Simply the best... task goes first...",
        // Spider-Man
        "With great priority comes great productivity...",
        // Avengers
        "I am inevitable... and so is task #1...",
        "I can rank this all day...",
        // Toy Story
        "To first place... and beyond!",
        // Gloria Gaynor
        "First I was afraid... but now I'm prioritized...",
        // Pat Benatar
        "Priority is a battlefield...",
        // Black Panther
        "Wakanda priority is this?!",
        // Taken
        "I have a very particular set of ranking skills...",
        // Ghostbusters
        "Who you gonna call? Prioritizers!"
    )

    private val memoToTasksMessages = listOf(
        // The Matrix
        "I know kung fu... and task extraction...",
        "There is no memo... only tasks...",
        "Follow the white rabbit... to your to-do list...",
        // Lord of the Rings
        "One does not simply write a memo... without tasks emerging...",
        "My precious... actionable items...",
        // Star Wars
        "These are the tasks you're looking for...",
        "I find your lack of checkboxes disturbing...",
        "May the tasks be with you...",
        "Help me Task-Wan, you're my only hope...",
        // Harry Potter
        "Accio tasks! Summoning your to-dos...",
        "I solemnly swear this memo is up to tasks...",
        // Transformers
        "Memos, roll out... into tasks!",
        // Jerry Maguire
        "Show me the tasks!",
        "You had me at 'to-do'...",
        // The Godfather
        "I'm gonna make you a task list you can't refuse...",
        // Toy Story
        "To productivity... and beyond!",
        // Ghostbusters
        "Who you gonna call? Task extractors!",
        "I ain't afraid of no memo!",
        // Avengers
        "I can convert memos all day...",
        // Taken
        "I have a very particular set of conversion skills...",
        // Black Panther
        "Wakanda tasks are these?!",
        // Apollo 13
        "Houston, we have tasks...",
        // Shrek
        "Memos are like onions... they have tasks...",
        // Sherlock Holmes
        "Elementary, my dear Watson... it's clearly a to-do list...",
        // Queen
        "We will... we will... task you!",
        "Another one gets the list...",
        // Michael Jackson
        "Just task it, task it...",
        // Rocky
        "Yo Adrian, I got tasks!"
    )

    private val suggestionsMessages = listOf(
        // The Matrix
        "What if I told you... I have ideas?",
        // Eurythmics
        "Sweet dreams are made of... suggestions...",
        // Star Wars
        "These are the suggestions you're looking for...",
        "Use the AI, Luke... it has ideas...",
        // Jerry Maguire
        "Show me the ideas!",
        "You had me at 'suggest'...",
        // Depeche Mode
        "Your own... personal... idea machine...",
        // The Godfather
        "I'm gonna make you a suggestion you can't refuse...",
        // Whitesnake
        "Here I go again... with more ideas...",
        // Toy Story
        "To ideas... and beyond!",
        // Avengers
        "I can suggest tasks all day...",
        // Journey
        "Don't stop believin'... in my suggestions...",
        // Def Leppard
        "Pour some ideas on me...",
        // Sherlock Holmes
        "Elementary, my dear... here are some ideas...",
        // The Police
        "Every task you make... I'll be suggesting...",
        // Lord of the Rings
        "My precious... suggestions...",
        // Black Panther
        "Wakanda ideas are these?!",
        // Foreigner
        "I wanna know what tasks you need...",
        // Taken
        "I have a very particular set of suggestion skills...",
        // Ghostbusters
        "Who you gonna call? Idea generators!"
    )

    private val planningMessages = listOf(
        // The Terminator
        "Come with me if you want to be productive...",
        "I'll be back... with a better schedule...",
        // Star Wars
        "Use the plan, Luke...",
        "These are the optimizations you're looking for...",
        "I find your lack of planning disturbing...",
        "May the productivity be with you...",
        // The Godfather
        "I'm gonna make you a plan you can't refuse...",
        "Leave the chaos, take the calendar...",
        // Jerry Maguire
        "Show me the schedule!",
        "You had me at 'plan my day'...",
        // The Matrix
        "What if I told you... there's a better way?",
        "I know kung fu... and time management...",
        "There is no overbooked. Only opportunity...",
        // Lord of the Rings
        "One does not simply wing it...",
        "My precious... free time slots...",
        "You shall not procrastinate!",
        // Avengers
        "I can plan this all day...",
        "I am inevitable... and so is task prioritization...",
        // Toy Story
        "To productivity... and beyond!",
        // The Dark Knight
        "It's not who you plan for, it's the plan...",
        "Why so unscheduled?",
        // Harry Potter
        "Accio productivity!",
        "I solemnly swear your day is manageable...",
        // Forrest Gump
        "Planning is like a box of chocolates...",
        "Run, schedule, run!",
        // Gladiator
        "Are you not optimized?!",
        // Top Gun
        "I feel the need... the need for time blocks...",
        // Ghostbusters
        "Who you gonna call? Day planners!",
        // Finding Nemo
        "Just keep planning, just keep planning...",
        // The Lion King
        "Hakuna matata... because I planned ahead...",
        // Black Panther
        "Wakanda schedule is this?!",
        // Queen
        "We will... we will... plan you!",
        "Another one optimizes the list...",
        // Taken
        "I have a very particular set of planning skills...",
        // Sherlock Holmes
        "Elementary, my dear... your day needs restructuring...",
        // Back to the Future
        "Great Scott! Your calendar looks better already...",
        "Where we're going, we don't need overwhelm..."
    )

    // Calendar sync messages - hamster + calendar/time themed
    private val syncingMessages = listOf(
        // Hamster + calendar context
        "Hamsters are fetching your meetings...",
        "Hamsters are rounding up your events...",
        "The hamsters found your calendar. Now they're reading it...",
        "Hamsters are cross-referencing time zones. Send help.",
        "Hamsters are arguing about which events come first...",
        "The hamsters discovered you have 47 meetings. They're concerned.",
        "Hamsters are untangling your schedule...",
        "One hamster is on Google, one is on Outlook. Teamwork.",
        // Calendar/time puns
        "Syncing your future... please hold.",
        "Rearranging the space-time continuum of your week...",
        "Teaching your calendars to get along...",
        "Convincing Google and Outlook to be friends...",
        "Your events are en route. Traffic was light.",
        "Downloading your commitments... no take-backs.",
        "Gathering your appointments from the cloud...",
        "Time is an illusion. Your calendar is not.",
        "Fetching events from the timeline...",
        "Your schedule is being assembled. Some batteries required.",
        "Reconciling your past, present, and future events...",
        "Pulling strings in the calendar dimension..."
    )

    private val syncCompleteMessages = listOf(
        // Hamster + calendar
        "Hamsters delivered your calendar. Tips appreciated.",
        "The hamsters synced it. They want you to know it wasn't easy.",
        "All events accounted for. Hamsters are napping.",
        "Hamsters have aligned your calendars. They're proud.",
        // Calendar/time puns
        "Your calendars are now in perfect harmony.",
        "Past, present, and future: all synced.",
        "Your schedule has arrived from the cloud.",
        "All your events are exactly where they should be.",
        "Time has been successfully organized.",
        "Calendars synced. The timeline is intact.",
        "Your week just got more organized.",
        "Events synchronized. Chaos averted."
    )

    // Notes-to-events matching messages (Smart Link feature)
    private val notesMatchingMessages = listOf(
        // The Matrix
        "I know kung fu... and note matching...",
        "There is no unlinked note... only matches waiting to happen...",
        // Lord of the Rings
        "One note to match them all...",
        "My precious... calendar connections...",
        // Star Wars
        "These are the matches you're looking for...",
        "I find your lack of links disturbing...",
        "May the match be with you...",
        // Jerry Maguire
        "Show me the matches!",
        "You had me at 'smart link'...",
        // The Godfather
        "I'm gonna make you a match you can't refuse...",
        // Ghostbusters
        "Who you gonna call? Note matchers!",
        // Sherlock Holmes
        "Elementary, my dear... this note belongs to that event...",
        // Taken
        "I have a very particular set of matching skills...",
        // Avengers
        "I can match notes all day...",
        // Finding Nemo
        "Just keep matching, just keep matching..."
    )

    // Summarization messages - condensing, distilling, TL;DR themes
    private val summarizationMessages = listOf(
        // The Matrix
        "I know kung fu... and TL;DR...",
        "There is no fluff... only essence...",
        "What if I told you... this could be shorter?",
        // Lord of the Rings
        "One summary to rule them all...",
        "My precious... key points...",
        "And my summary! (It's shorter than yours)",
        // Star Wars
        "These are the key points you're looking for...",
        "I find your lack of brevity disturbing...",
        "The summary is strong with this one...",
        "May the TL;DR be with you...",
        "Help me Summary-Wan, you're my only hope...",
        // Jerry Maguire
        "Show me the highlights!",
        "You had me at 'summarize'...",
        // The Godfather
        "I'm gonna make you a summary you can't refuse...",
        "Leave the fluff, take the essence...",
        // Harry Potter
        "Accio main points!",
        "I solemnly swear to keep this brief...",
        // Terminator
        "I'll be back... with fewer words...",
        "Hasta la vista, verbosity...",
        // Toy Story
        "To brevity... and beyond!",
        // Avengers
        "I can summarize this all day...",
        "I am inevitable... and so is conciseness...",
        // Ghostbusters
        "Who you gonna call? Summarizers!",
        "I ain't afraid of no long text!",
        // Taken
        "I have a very particular set of condensing skills...",
        // Black Panther
        "Wakanda summary is this?!",
        // A Few Good Men
        "You can't handle the full version!",
        // Forrest Gump
        "Summarizing is like a box of chocolates...",
        // Finding Nemo
        "Just keep condensing, just keep condensing...",
        // The Lion King
        "Hakuna matata... it's now shorter...",
        // Top Gun
        "I feel the need... the need for brevity...",
        // Apollo 13
        "Houston, we have a summary...",
        // Jaws
        "You're gonna need a smaller paragraph...",
        // Queen
        "We will... we will... summarize you!",
        "Another word bites the dust...",
        // Sherlock Holmes
        "Elementary, my dear... here's the gist..."
    )

    // Translation messages - language, multilingual, interpretation themes
    private val translationMessages = listOf(
        // The Matrix
        "I know kung fu... in 50 languages...",
        "There is no language barrier... only opportunity...",
        "What if I told you... I speak everything?",
        // Lord of the Rings
        "One language to rule them all...",
        "My precious... fluency...",
        "You shall not mispronounce!",
        // Star Wars
        "These are the words you're looking for...",
        "I find your lack of translation disturbing...",
        "The polyglot is strong with this one...",
        "May the translation be with you...",
        "C-3PO approves this translation...",
        // Jerry Maguire
        "Show me the palabras!",
        "You had me at 'translate'...",
        // The Godfather
        "I'm gonna make you a translation you can't refuse...",
        "Leave the original, take the translation...",
        // Harry Potter
        "Accio translation!",
        "I solemnly swear this is accurate...",
        // Terminator
        "I'll be back... in another language...",
        "Hasta la vista... wait, that IS Spanish!",
        // Toy Story
        "To fluency... and beyond!",
        // Avengers
        "I can translate this all day...",
        // Ghostbusters
        "Who you gonna call? Translators!",
        "I ain't afraid of no foreign text!",
        // Taken
        "I have a very particular set of linguistic skills...",
        // Black Panther
        "Wakanda language is this?!",
        // Finding Nemo
        "Just keep translating, just keep translating...",
        // The Lion King
        "Hakuna matata... means no worries in Swahili!",
        // Apollo 13
        "Houston, we have translation...",
        // Lost in Translation
        "Lost in translation? Not on my watch...",
        "For relaxing times... make it translation time...",
        // Queen
        "We will... we will... translate you!",
        "Bohemian Rhapsody sounds great in any language...",
        // Sherlock Holmes
        "Elementary, my dear... just in French...",
        // Dr. Strangelove
        "Mein Führer! I can translate!",
        // Casablanca
        "Here's looking at you... in Italian...",
        // The Wizard of Oz
        "Toto, I don't think we're in English anymore..."
    )

    private val generalMessages = listOf(
        // Queen
        "Is this the real result? Is this just processing?",
        "We will... we will... process you!",
        "Don't stop me now, I'm having such a good compute...",
        "Another task bites the dust...",
        // Michael Jackson
        "Just process it, process it...",
        "Are you OK? Are you OK, server?",
        // Madonna
        "Processing for the very first time...",
        "Living in a digital world, and I am a digital server...",
        // Bon Jovi
        "Whoa-oh! We're halfway there... processing!",
        // Journey
        "Don't stop believin'... in my servers...",
        // Survivor
        "It's the eye of the processor...",
        // A-ha
        "Take on me... take on my processing...",
        // Eurythmics
        "Sweet dreams are made of bytes...",
        // Depeche Mode
        "Your own... personal... AI...",
        // Cyndi Lauper
        "Servers just wanna have fun...",
        // The Police
        "Every byte you make... I'll be processing you...",
        // Rick Astley
        "Never gonna give you up... never gonna let you timeout...",
        // Nirvana
        "Smells like server spirit...",
        // Whitney Houston
        "And I-ee-I will always process you...",
        // Toto
        "I bless the rains down in the server room...",
        // Def Leppard
        "Pour some data on me...",
        "Armageddon it! ...your results, I mean...",
        "Love bites... but my processing doesn't...",
        // Van Halen
        "Might as well process!",
        // Guns N' Roses
        "Sweet data of mine...",
        "Welcome to the server jungle...",
        "All I need is just a little patience... processing...",
        "Take me down to the processor city...",
        // Duran Duran
        "Hungry like the server...",
        "Her name is Rio and she's processing...",
        "Save a prayer for the server tonight...",
        // Billy Joel
        "We didn't start the server... it was always running...",
        // Backstreet Boys
        "Tell me why... ain't nothing but a process...",
        // Spice Girls
        "I'll tell you what I want... your patience...",
        // Oasis
        "You're gonna be the one that waits...",
        // Aerosmith
        "I don't wanna miss a byte...",
        // Talking Heads
        "And you may ask yourself... how did I process this?",
        // Bee Gees
        "Ah, ah, ah, ah, stayin' online... stayin' online...",
        // David Bowie
        "Ground control to major server...",
        // Forrest Gump
        "Processing is like a box of chocolates...",
        "Run, process, run!",
        // Jurassic Park
        "Data, uh... finds a way...",
        // The Shining
        "Heeeere's your data!",
        // Dirty Dancing
        "Nobody puts data in a corner...",
        // Titanic
        "I'm the king of the server!",
        // Gladiator
        "Are you not processed?!",
        // Fight Club
        "First rule of AI club: we don't talk about latency...",
        // Finding Nemo
        "Just keep processing, just keep processing...",
        // The Lion King
        "Hakuna matadata... it means no worries...",
        // Anchorman
        "I'm kind of a big deal... in the cloud...",
        // Star Trek
        "Make it so... number one...",
        // The X-Files
        "The data is out there...",
        // The Mandalorian
        "This is the way... of the algorithm...",
        // Game of Thrones
        "Processing is coming...",
        // Breaking Bad
        "I am the one who processes...",
        // Seinfeld
        "No timeout for you!",
        // Tears for Fears
        "Everybody wants to rule the server...",
        // Phil Collins
        "I can feel it processing in the air tonight...",
        // Lord of the Rings
        "You shall not timeout!",
        // Harry Potter
        "Expecto results!",
        // Casablanca
        "Here's looking at you, data...",
        // The Wizard of Oz
        "There's no place like the cloud..."
    )

    // ========================================================================
    // Shuffle Bag Getters - No repeats until all messages in category shown
    // ========================================================================

    fun getRandomAudioMessage(): String {
        if (audioMessageQueue.isEmpty()) {
            audioMessageQueue.addAll(audioMessages.shuffled())
        }
        return audioMessageQueue.removeAt(0)
    }

    fun getRandomGeneralMessage(): String {
        if (generalMessageQueue.isEmpty()) {
            generalMessageQueue.addAll(generalMessages.shuffled())
        }
        return generalMessageQueue.removeAt(0)
    }

    fun getRandomOcrMessage(): String {
        if (ocrMessageQueue.isEmpty()) {
            ocrMessageQueue.addAll(ocrMessages.shuffled())
        }
        return ocrMessageQueue.removeAt(0)
    }

    fun getRandomBreakdownMessage(): String {
        if (breakdownMessageQueue.isEmpty()) {
            breakdownMessageQueue.addAll(breakdownMessages.shuffled())
        }
        return breakdownMessageQueue.removeAt(0)
    }

    fun getRandomPrioritizationMessage(): String {
        if (prioritizationMessageQueue.isEmpty()) {
            prioritizationMessageQueue.addAll(prioritizationMessages.shuffled())
        }
        return prioritizationMessageQueue.removeAt(0)
    }

    fun getRandomSuggestionsMessage(): String {
        if (suggestionsMessageQueue.isEmpty()) {
            suggestionsMessageQueue.addAll(suggestionsMessages.shuffled())
        }
        return suggestionsMessageQueue.removeAt(0)
    }

    fun getRandomMemoToTasksMessage(): String {
        if (memoToTasksMessageQueue.isEmpty()) {
            memoToTasksMessageQueue.addAll(memoToTasksMessages.shuffled())
        }
        return memoToTasksMessageQueue.removeAt(0)
    }

    fun getRandomPlanningMessage(): String {
        if (planningMessageQueue.isEmpty()) {
            planningMessageQueue.addAll(planningMessages.shuffled())
        }
        return planningMessageQueue.removeAt(0)
    }

    fun getRandomSyncingMessage(): String {
        if (syncingMessageQueue.isEmpty()) {
            syncingMessageQueue.addAll(syncingMessages.shuffled())
        }
        return syncingMessageQueue.removeAt(0)
    }

    fun getRandomSyncCompleteMessage(): String {
        if (syncCompleteMessageQueue.isEmpty()) {
            syncCompleteMessageQueue.addAll(syncCompleteMessages.shuffled())
        }
        return syncCompleteMessageQueue.removeAt(0)
    }

    fun getRandomNotesMatchingMessage(): String {
        if (notesMatchingMessageQueue.isEmpty()) {
            notesMatchingMessageQueue.addAll(notesMatchingMessages.shuffled())
        }
        return notesMatchingMessageQueue.removeAt(0)
    }

    fun getRandomSummarizationMessage(): String {
        if (summarizationMessageQueue.isEmpty()) {
            summarizationMessageQueue.addAll(summarizationMessages.shuffled())
        }
        return summarizationMessageQueue.removeAt(0)
    }

    fun getRandomTranslationMessage(): String {
        if (translationMessageQueue.isEmpty()) {
            translationMessageQueue.addAll(translationMessages.shuffled())
        }
        return translationMessageQueue.removeAt(0)
    }
}