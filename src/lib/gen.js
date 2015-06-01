import {exitIfError} from "./util"
import {copyDir} from "./fs"
import * as nomnom from "nomnom"
import * as fs from "fs"
import * as path from "path"
import * as hogan from "hogan.js"
import * as readline from "readline"

function findRoots() {
  let res;
  exitIfError(() => {
    const home = process.env.HOME || process.env.USERPROFILE;
    let _;
    _ = path.join(home, ".genappconfig");
    _ = fs.readFileSync(_, {encoding: "utf8"});
    _ = JSON.parse(_).roots;
    _ = _.map(root => root.replace(/^~/, home));
    res = _;
  });
  return res;
}

function listBundles() {
  function extractBundleNames() {
    let names;
    exitIfError(() => {
      let _;
      _ = findRoots();
      _ = _.mapcat(root => {
        try {
          return fs.readdirSync(root)
            .filter(file =>
              fs.statSync(path.join(root, file)).isDirectory() &&
              file[0] !== ".");
        } catch (err) {
          return null;
        }
      });
      _ = new Set(_);
      names = _;
    });
    return names;
  }

  for (let name of extractBundleNames()) console.log(name);
}

function generateBundle(bundle, output) {
  function findBundleDir(bundle) {
    for (let root of findRoots()) {
      try {
        if (root && fs.readdirSync(root).indexOf(bundle) > -1) {
          return path.join(root, bundle);
        }
      } catch (err) {
        // Ignore and try the next.
      }
    }
    console.log(`${bundle} could not be found.`);
    process.exit(0);
  }

  function mustacheTransform(context, template) {
    return hogan.compile(template).render(context);
  }

  async function constructContext(spec) {
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout
    });

    function question(query) {
      return new Promise((resolve, reject) => {
        rl.question(query, answer => resolve(answer));
      });
    }

    async function parseContext(spec, ctx={}, prefix="") {
      for (const k in spec) {
        const v = spec[k];
        const type = typeof(v);

        switch (type) {
        case "string":
          ctx[k] = await question(`${prefix}${k}? `);
          break;
        case "number": {
          while (true) {
            const n = +(await question(`${prefix}${k}? (number) `))
            if (isNaN(n)) {
              console.log("It's not number.");
            } else {
              ctx[k] = n;
              break;
            }
          }
          break;
        }
        case "boolean": {
          while (true) {
            let s = (await question(`${prefix}${k}? (yes/no) `)).toLowerCase();
            if ((s === "yes") || (s === "y")) {
              ctx[k] = true;
              break;
            } else if ((s === "no") || (s === "n")) {
              ctx[k] = false;
              break;
            } else {
              console.log("yes or no please.");
            }
          }
          break;
        }
        case "function":
          ctx[k] = v;
          break;
        default:
          ctx[k] = {};
          await parseContext(v, ctx[k], k + ">");
          break;
        }
      }
      return ctx;
    }

    try {
      return await parseContext(spec);
    } finally {
      rl.close();
    }
  }

  function postProcessContext(postProcessFn, context) {
    if (typeof(postProcessFn) !== "function") return context;
    return new Promise((resolve, reject) => {
      postProcessFn(context, () => resolve());
    });
  }

  exitIfError(async () => {
    const bundleDir = findBundleDir(bundle);
    const bundleContext = require(bundleDir + ".js");
    let _;
    _ = bundleContext.context;
    _ = await constructContext(_);
    await postProcessContext(bundleContext.postProcess, _);
    const context = _;
    const transform = mustacheTransform.bind(null, context);

    copyDir(bundleDir, output || ".", transform, transform);
  });
}

const opts = nomnom
  .script("gen")
  .options({
    list: {
      abbr: "l",
      flag: true,
      help: "List bundles",
      callback() {
        listBundles();
        process.exit(0);
      }
    },
    version: {
      abbr: "V",
      flag: true,
      help: "Version number",
      callback() {
        console.log(`gen v${require("../package.json").version}`);
        process.exit(0);
      }
    },
    bundle: {
      position: 0,
      required: true,
      help: "Bundle to generate"
    },
    output: {
      position: 1,
      "default": ".",
      help: "Output directory"
    }
  })
  .parse();

generateBundle(opts.bundle, opts.output);
