import { copyFileSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, extname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const srcDir = join(root, "src");
const distDir = join(root, "dist");
const projectConfigPath = join(root, "project.config.json");
const distProjectConfigPath = join(distDir, "project.config.json");
const skippedDirectories = new Set(["__tests__"]);

rmSync(distDir, { recursive: true, force: true });
mkdirSync(distDir, { recursive: true });

function ensureParentDirectory(targetPath) {
  mkdirSync(dirname(targetPath), { recursive: true });
}

function formatDiagnostic(diagnostic) {
  const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n");
  if (!diagnostic.file || diagnostic.start === undefined) {
    return message;
  }

  const position = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
  return `${diagnostic.file.fileName}:${position.line + 1}:${position.character + 1} - ${message}`;
}

function emitTypeScript(sourcePath, targetPath) {
  const source = readFileSync(sourcePath, "utf8");
  const result = ts.transpileModule(source, {
    fileName: sourcePath,
    reportDiagnostics: true,
    compilerOptions: {
      target: ts.ScriptTarget.ES2021,
      module: ts.ModuleKind.CommonJS,
      moduleResolution: ts.ModuleResolutionKind.Node10,
      strict: true,
      esModuleInterop: false,
      sourceMap: false,
      removeComments: false
    }
  });

  const errors = (result.diagnostics ?? []).filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error);
  if (errors.length > 0) {
    throw new Error(errors.map(formatDiagnostic).join("\n"));
  }

  ensureParentDirectory(targetPath);
  writeFileSync(targetPath, result.outputText, "utf8");
}

function emitSourceTree(currentDir) {
  for (const entry of readdirSync(currentDir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!skippedDirectories.has(entry.name)) {
        emitSourceTree(join(currentDir, entry.name));
      }
      continue;
    }

    if (!entry.isFile()) {
      continue;
    }

    const sourcePath = join(currentDir, entry.name);
    const relativePath = relative(srcDir, sourcePath);
    const targetPath = join(distDir, relativePath);

    if (extname(entry.name) === ".ts") {
      emitTypeScript(sourcePath, targetPath.replace(/\.ts$/, ".js"));
      continue;
    }

    ensureParentDirectory(targetPath);
    copyFileSync(sourcePath, targetPath);
  }
}

emitSourceTree(srcDir);

const projectConfig = JSON.parse(readFileSync(projectConfigPath, "utf8"));
projectConfig.miniprogramRoot = "./";
writeFileSync(distProjectConfigPath, `${JSON.stringify(projectConfig, null, 2)}\n`, "utf8");

writeFileSync(
  join(distDir, "BUILD_README.md"),
  [
    "# 小程序构建产物",
    "",
    "该目录由 `pnpm --filter @wecom-bft/miniprogram build` 生成。",
    "微信开发者工具可直接导入本目录，`project.config.json` 的 `miniprogramRoot` 已指向 `./`。",
    ""
  ].join("\n"),
  "utf8"
);
