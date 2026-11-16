/** A message with {0} in it, e.g. "{0} selected", filled in. */
export const fill = (message, value) => message.replace('{0}', value);

/** The word with its first letter in capitals: "one" as "One". */
export const capitalize = (word) => word.charAt(0).toUpperCase() + word.slice(1);
