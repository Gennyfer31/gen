import {exitIfError} from "./util"
import "./arr"
import {readdirSync, readFileSync, writeFileSync, statSync} from "fs"
import * as path from "path"
import * as mkdirp from "mkdirp"

export function* fileSeq(dir) {
  let _;
  _ = readdirSync(dir);
  _ = _.map(file => path.join(dir, file));
  _ = _.groupBy(file => statSync(file).isDirectory());
  const {true: subdirs, false: files} = _;
  if (files) for (let file of files) yield file;
  if (subdirs) for (let subdir of subdirs) yield* fileSeq(subdir);
}

export function copyDir(input, out, contentTransform, pathTransform) {
  function removeBaseDir(file) {
    return file.substring(input.length + 1);
  }

  function copyFile(file) {
    const from = path.join(input, file);
    const to = path.join(out, pathTransform(file));

    mkdirp.sync(path.dirname(to));

    let _;
    _ = readFileSync(from, {encoding: "utf8"});
    _ = contentTransform(_);
    writeFileSync(to, _);
  }

  exitIfError(() => {
    for (let file of fileSeq(input)) copyFile(removeBaseDir(file));
  });
}
