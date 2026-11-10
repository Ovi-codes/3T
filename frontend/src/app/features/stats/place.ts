/** "1st", "2nd", "3rd", "4th" … "11th", "12th", "13th" … "21st". */
export function ordinal(place: number): string {
  const lastTwo = place % 100;
  if (lastTwo >= 11 && lastTwo <= 13) {
    return `${place}th`;
  }
  switch (place % 10) {
    case 1:
      return `${place}st`;
    case 2:
      return `${place}nd`;
    case 3:
      return `${place}rd`;
    default:
      return `${place}th`;
  }
}

/** A runner's place against the field: "2nd of 31". */
export function placeOf(run: { position: number; finishers: number }): string {
  return `${ordinal(run.position)} of ${run.finishers}`;
}
