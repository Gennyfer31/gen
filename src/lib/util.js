export function exitIfError(f) {
  try {
    f();
  } catch (err) {
    console.error(`ERR: ${err}`);
    process.exit(1);
  }
}
