FROM node:18-alpine as build_esignet_ui

# Set a build-time environment variable (replace YOUR_ENV_VARIABLE_NAME with the desired variable name)
ARG oidcUIPublicUrl
ARG uiDefaultTheme
ARG defaultLang
ARG defaultWellknown
ARG defaultFevicon
ARG defaultTitle
ARG defaultIdProviderName

ENV OIDC_UI_PUBLIC_URL=$oidcUIPublicUrl
ENV DEFAULT_THEME=$uiDefaultTheme
ENV DEFAULT_LANG=$defaultLang
ENV DEFAULT_WELLKNOWN=$defaultWellknown
ENV DEFAULT_FEVICON=$defaultFevicon
ENV DEFAULT_TITLE=$defaultTitle
ENV DEFAULT_ID_PROVIDER_NAME=$defaultIdProviderName

# Set the environment variable as a placeholder for PUBLIC_URL
ENV PUBLIC_URL=_PUBLIC_URL_

COPY package*.json ./
RUN npm install --legacy-peer-deps
#Copy the working directory
COPY . ./
RUN npm run build

FROM nginx:1.28.0-alpine3.21-slim

RUN apk update \
    && apk add --no-cache shadow curl npm wget unzip zip wget

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user=mosip

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_group=mosip

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_uid=1001

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_gid=1001

# can be passed during Docker build as build time environment for artifactory URL
ARG i18n_url
ARG theme_url
ARG images_url

# environment variable to pass artifactory url, at docker runtime
ENV i18n_url_env=${i18n_url}
ENV theme_url_env=${theme_url}
ENV images_url_env=${images_url}

ENV nginx_dir=/usr/share/nginx

ENV work_dir=${nginx_dir}/html

ENV i18n_path=${work_dir}/locales

ENV theme_path=${work_dir}/theme

ENV image_path=${work_dir}/images

ENV plugins_path=${nginx_dir}/html/plugins

ENV plugins_format=iife

# set working directory for the user
WORKDIR /home/${container_user}

# install packages and create user
RUN groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && mkdir -p /var/run/nginx /var/tmp/nginx ${i18n_path} ${theme_path} ${image_path} ${plugins_path} ${plugins_path}/temp \
    && chown -R ${container_user}:${container_user} /usr/share/nginx /var/run/nginx /var/tmp/nginx ${i18n_path} ${theme_path} ${image_path} ${plugins_path} ${plugins_path}/temp

ADD configure_start.sh configure_start.sh

RUN chmod +x configure_start.sh

COPY ./nginx/nginx.conf /etc/nginx/nginx.conf

# copy build files to nginx html directory
COPY --from=build_esignet_ui /build  ${work_dir}

RUN echo "DEFAULT_THEME=$DEFAULT_THEME" >> ${work_dir}/env.env \
    && echo "DEFAULT_LANG=$DEFAULT_LANG" >> ${work_dir}/env.env \
    && echo "DEFAULT_WELLKNOWN=$DEFAULT_WELLKNOWN" >> ${work_dir}/env.env \
    && echo "DEFAULT_FEVICON=$DEFAULT_FEVICON" >> ${work_dir}/env.env env \
    && echo "DEFAULT_TITLE=$DEFAULT_TITLE" >> ${work_dir}/env.env \
    && echo "DEFAULT_ID_PROVIDER_NAME=$DEFAULT_ID_PROVIDER_NAME" >> ${work_dir}/env.env \
    && wget --no-check-certificate --no-cache --no-cookies https://raw.githubusercontent.com/mosip/artifactory-ref-impl/master/artifacts/src/mosip-plugins/sign-in-with-esignet/sign-in-with-esignet.zip -O ${plugins_path}/temp/sign-in-button-plugin.zip

RUN chown -R ${container_user}:${container_user} /home/${container_user}

# change permissions of file inside working dir
RUN chown -R ${container_user}:${container_user} ${work_dir}

# select container user for all tasks
USER ${container_user_uid}:${container_user_gid}

EXPOSE 3000

ENTRYPOINT [ "sh", "configure_start.sh" ]

CMD echo "starting nginx" ; \
    nginx ; \
    sleep infinity
