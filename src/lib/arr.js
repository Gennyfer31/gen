Array.prototype.groupBy = function groupBy(classifier) {
  let grouped = {};
  for (let x of this) {
    const key = classifier(x);
    if (!grouped[key]) grouped[key] = [];
    grouped[key].push(x);
  }
  return grouped;
};

Array.prototype.mapcat = function mapcat(f) {
  return this.map(f)
    .reduce((a, b) => {
      if (a == null) {
        return b;
      } else if (b == null) {
        return a;
      } else {
        return a.concat(b);
      }
    });
}
