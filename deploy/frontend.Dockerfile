# 构建并打包所有 H5/admin 前端到一个 nginx 静态文件镜像
# Build context = 仓库根目录
FROM node:20-alpine AS build
WORKDIR /workspace

# corepack 启用 pnpm（lock 文件需要 pnpm）
RUN corepack enable && corepack prepare pnpm@10 --activate

# 复制 pnpm 元数据先装依赖（利用 docker layer cache）
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend/shared/package.json ./frontend/shared/
COPY frontend/admin/package.json ./frontend/admin/
RUN pnpm install --frozen-lockfile

# 复制实际源码
COPY frontend/shared ./frontend/shared
COPY frontend/admin ./frontend/admin

# 构建 admin（产物在 frontend/admin/dist，vite base=/admin/）
RUN pnpm --filter @wecom-bft/admin build

# nginx 阶段：把构建产物拷到 /usr/share/nginx/html/admin
FROM nginx:1.27-alpine
COPY --from=build /workspace/frontend/admin/dist /usr/share/nginx/html/admin
# Phase B 新增 H5 后再追加 COPY 行 (--from=build /workspace/frontend/wecom-sidebar/dist /.../wecom-sidebar 等)

# 默认 nginx 配置由 docker-compose volume mount 注入
EXPOSE 80 443
