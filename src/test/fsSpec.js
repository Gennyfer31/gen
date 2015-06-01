require("source-map-support").install();

import {fileSeq} from "../lib/fs"

describe("fs", () => {
  it("fileSeq yields", () => {
    expect(Array.from(fileSeq("./src")).length).toBeGreaterThan(0);
  });
});
