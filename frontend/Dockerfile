FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --only=production=false

COPY . .

RUN npm run build

# Serve with nginx
FROM nginx:alpine

# Copy built files from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN echo 'server { \
    listen 80; \
    server_name localhost; \
    root /usr/share/nginx/html; \
    index index.html; \
    location / { \
        try_files $uri $uri/ /index.html; \
    } \
    # Enable gzip compression \
    gzip on; \
    gzip_vary on; \
    gzip_min_length 1024; \
    gzip_types text/plain text/css text/xml text/javascript application/x-javascript application/xml+rss application/javascript application/json; \
}' > /etc/nginx/conf.d/default.conf

CMD ["nginx", "-g", "daemon off;"]
