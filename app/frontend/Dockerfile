# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Baked into the bundle at build time (the browser calls the backend directly).
ARG VITE_API_BASE_URL=http://localhost:8080/api
ARG VITE_STUDIO_NAME=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_STUDIO_NAME=$VITE_STUDIO_NAME
RUN npm run build

# ---- serve ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
