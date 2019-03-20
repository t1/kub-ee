FROM ubuntu

RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install httpie telnet iputils-ping vim jq nginx

RUN useradd nginx

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
STOPSIGNAL SIGTERM
CMD ["nginx", "-g", "daemon off;"]
