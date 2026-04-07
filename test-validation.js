const assert = require('assert');

// A valid WireGuard public key (32 bytes)
const validKey = 'G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=';
console.log('Valid key length:', Buffer.from(validKey, 'base64').length);
console.log('Regex match:', /^[A-Za-z0-9+/]{43}=$/.test(validKey));

// An injected key (silently ignored by Buffer.from)
const injectedKey = validKey.substring(0, 42) + "+='";
console.log('Injected key:', injectedKey);
console.log('Injected key length:', Buffer.from(injectedKey, 'base64').length);
console.log('Injected regex match:', /^[A-Za-z0-9+/]{43}=$/.test(injectedKey));
