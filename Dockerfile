FROM navikt/java:13-appdynamics

ENV APPD_ENABLED=true

COPY docker-init-scripts/import-azure-credentials.sh /init-scripts/20-import-azure-credentials.sh
COPY docker-init-scripts/import-serviceuser-credentials.sh /init-scripts/21-import-serviceuser-credentials.sh
COPY docker-init-scripts/import-appdynamics-settings.sh /init-scripts/22-import-appdynamics-settings.sh


COPY target/app.jar ./
