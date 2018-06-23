FROM theasp/clojurescript-nodejs:shadow-cljs-alpine as build
WORKDIR /app
RUN apk --no-cache add python alpine-sdk postgresql-dev git
COPY package.json package-lock.json shadow-cljs.edn deps.edn /app/
RUN npm install -g shadow-cljs && npm install --save-dev shadow-cljs && shadow-cljs npm-deps && npm install && echo "nil" | clojure -
COPY ./ /app
RUN find .shadow-cljs && npm install --save-dev shadow-cljs && find .shadow-cljs && shadow-cljs release client && shadow-cljs release server

FROM node:alpine
WORKDIR /app
ENV DB__NAME="lapidary" DB__HOSTNAME="postgres" DB__USER="lapidary" DB__PASSWORD="lapidary" HTTP__PORT="80" HTTP__ADDRESS="0.0.0.0"
EXPOSE 80
CMD ["./run-server.sh"]
RUN apk --no-cache add libpq bash
COPY --from=build /app/ /app/
