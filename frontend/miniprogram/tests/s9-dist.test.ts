import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import packageJson from "../package.json";
import { buildMiniprogramDist } from "../scripts/build-dist.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

describe("S9 miniprogram dist build", () => {
  it("keeps the miniprogram build producing a dist artifact", () => {
    const projectConfig = JSON.parse(readFileSync(join(root, "project.config.json"), "utf8")) as {
      miniprogramRoot?: string;
    };

    expect(packageJson.scripts.build).toContain("scripts/build-dist.mjs");
    expect(projectConfig.miniprogramRoot).toBe("dist/");
  });

  it("emits WeChat-loadable JavaScript for every registered page", () => {
    buildMiniprogramDist();

    expect(existsSync(join(root, "dist/pages/courses/list.js"))).toBe(true);
    expect(existsSync(join(root, "dist/pages/courses/list.ts"))).toBe(false);
    expect(existsSync(join(root, "dist/app.js"))).toBe(true);
  });
});
