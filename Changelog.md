# Changelog

## 1.2.3

- [#14](https://github.com/qaware/go-offline-maven-plugin/issues/14) fixed Dependencies not resolving correctly when depending on a reactor artifact and changing one of the transitive dependencies of that artifact via dependency management

## 1.2.2

- [#15](https://github.com/qaware/go-offline-maven-plugin/issues/15) added support for classifier to DynamicDependency configuration 

## 1.2.1
- [#10](https://github.com/qaware/go-offline-maven-plugin/issues/10) Fixed regression in 1.2.0: Only transitive dependencies of plugins and dynamicDependencies where downloaded,
not the artifact itself.

## 1.2.0

- added ability to fail on errors
- rewrote how dependencies are downloaded. Should fix concurrency issues with broken downloads. 

##1.1.0

- [#4](https://github.com/qaware/go-offline-maven-plugin/issues/4) Added downloadJavadoc feature
- [#3](https://github.com/qaware/go-offline-maven-plugin/issues/3) Fixed typos and improve readablility of Readme (thanks [vlow](https://github.com/vlow))    

## 1.0

- initial version