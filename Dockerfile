FROM theasp/clojurescript-nodejs:shadow-cljs-alpine as build
WORKDIR /app
RUN apk --no-cache add python alpine-sdk postgresql-dev git
COPY package.json package-lock.json shadow-cljs.edn /app/
RUN shadow-cljs npm-deps && npm install --save-dev shadow-cljs && npm install
COPY ./ /app
RUN shadow-cljs release client server

FROM node:alpine
WORKDIR /app
ENV DB_NAME="lapidary" DB_HOST="postgres" DB_USER="lapidary" DB_PASSWORD="lapidary" WEB_FQDN="lapidary.example.com" HTTP_PORT="80"
EXPOSE 80
CMD ["./run-server.sh"]
RUN apk --no-cache add libpq bash
COPY --from=build /app/ /app/
