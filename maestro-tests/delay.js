// Simple delay script for e-ink refresh
// Uses busy-wait since Thread.sleep isn't available in Maestro JS

var start = Date.now();
var delayMs = DELAY_MS || 2000;

while (Date.now() - start < delayMs) {
    // busy wait
}

output.delayed = true;
