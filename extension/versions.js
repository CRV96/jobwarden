// Versions as JobWarden's releases are named: 0.2.0, or v0.2.0 as a tag. One with a suffix, e.g. 0.2.0-SNAPSHOT,
// comes before the release. Like the server's Version class.

/**
 * @returns {boolean} whether `newer` is a later version than `older`; false when either isn't a version
 */
export function isNewer(newer, older) {
  const a = parse(newer);
  const b = parse(older);
  if (!a || !b) {
    return false;
  }
  for (let i = 0; i < Math.max(a.numbers.length, b.numbers.length); i++) {
    const difference = (a.numbers[i] ?? 0) - (b.numbers[i] ?? 0);
    if (difference !== 0) {
      return difference > 0;
    }
  }
  return a.suffix === null && b.suffix !== null;
}

function parse(text) {
  const match = /^v?(\d+(?:\.\d+){0,3})(?:-([0-9A-Za-z.-]+))?$/.exec(String(text ?? '').trim());
  return match ? { numbers: match[1].split('.').map(Number), suffix: match[2] ?? null } : null;
}
