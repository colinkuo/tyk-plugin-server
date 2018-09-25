# install tyk-cli https://github.com/TykTechnologies/tyk-cli
tyk-cli bundle build -y -o ./bundle.zip

md5 ./bundle.zip

mv -f -v ./bundle.zip ../../src/main/resources/bundles/
