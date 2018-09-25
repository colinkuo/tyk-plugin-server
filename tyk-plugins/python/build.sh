# install tyk-cli https://github.com/TykTechnologies/tyk-cli
tyk-cli bundle build -y -o ./python-bundle.zip
#mv -f -v bundle.zip ./src/main/resources/bundles
echo "check the new md5sum of new bundle file..."
md5 ./python-bundle.zip
mv -f -v ./python-bundle.zip ../../src/main/resources/bundles/
