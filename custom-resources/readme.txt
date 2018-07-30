To install on local maven
mvn install:install-file -Dfile=owlapi-osgidistribution-4.2.8-SNAPSHOT.jar



or

mvn install:install-file -Dfile=owlapi-osgidistribution-4.2.8-SNAPSHOT.jar -DpomFile=<one of the two poms...>

or

mvn install:install-file -Dfile=<path-to-file> -DgroupId=<group-id> \
    -DartifactId=<artifact-id> -Dversion=<version> -Dpackaging=<packaging>
