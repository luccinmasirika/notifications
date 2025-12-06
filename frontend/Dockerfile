FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

RUN npm run build -- --configuration production

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

COPY --from=builder /app/dist/notifications-frontend/browser /usr/share/nginx/html

RUN mkdir -p /usr/share/nginx/html/assets

EXPOSE 80

ENTRYPOINT ["/entrypoint.sh"]

