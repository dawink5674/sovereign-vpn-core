const crypto = require('crypto');

function checkAuth(clientApiKey, adminApiKey) {
  if (!adminApiKey) return 500;
  if (!clientApiKey) return 401;

  const clientBuf = Buffer.from(clientApiKey);
  const adminBuf = Buffer.from(adminApiKey);

  if (clientBuf.length !== adminBuf.length || !crypto.timingSafeEqual(clientBuf, adminBuf)) {
    return 401;
  }
  return 200;
}

console.log(checkAuth("test", "test"));
console.log(checkAuth("test", "test2"));
console.log(checkAuth("", "test2"));
console.log(checkAuth(undefined, "test2"));
console.log(checkAuth("test", undefined));
