const assert = require('assert');

function validateKey(key) {
  return /^[A-Za-z0-9+/]{43}=$/.test(key);
}

assert(validateKey('G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=') === true);
assert(validateKey('G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5W=') === false);
assert(validateKey('G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=;rm -f test;') === false);
console.log('Regex tests passed');
