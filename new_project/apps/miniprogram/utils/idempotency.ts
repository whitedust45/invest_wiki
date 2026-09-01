const CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

/** Generates a client request key only; the backend remains the authority for deduplication. */
export function newIdempotencyKey(): string {
  let key = "";
  for (let index = 0; index < 26; index += 1) {
    key += CROCKFORD.charAt(Math.floor(Math.random() * CROCKFORD.length));
  }
  return key;
}
