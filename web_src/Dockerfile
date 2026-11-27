FROM nginx:stable AS stage

ADD nginx.conf /etc/nginx/nginx.conf
# ADD default.conf /etc/nginx/conf.d/default.conf

COPY /dist /home/vue_server/

RUN ln -sf /dev/stdout /var/log/nginx/access.log && ln -sf /dev/stderr /var/log/nginx/error.log

EXPOSE 8080
STOPSIGNAL SIGTERM
CMD ["nginx","-g","daemon off;"]
