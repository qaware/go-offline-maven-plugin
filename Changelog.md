# Changelog

## 1.2.7

- [#22](https://github.com/qaware/go-offline-maven-plugin/issues/22) Added \<type\> attribute to DynamicDependency

## 1.2.6

- [#20](https://github.com/qaware/go-offline-maven-plugin/issues/20) Validate the user provided DynamicDependency configuration and print an error to help the user fix the configuration if it is incorrect.

## 1.2.6

- fixed: Errors when downloading dependencies completely skipped the download of plugins.

## 1.2.4

- [#16](https://github.com/qaware/go-offline-maven-plugin/issues/16) fixed attachedArtifacts with different types/extensions than the main artifact not recognized as part of the reactor build.
- [#16](https://github.com/qaware/go-offline-maven-plugin/issues/16) fixed: 'IgnoreArtifactDescriptorRepositories' flag was always set to true instead of using maven provided value. This could lead to  unresolvable artifacts if a dependency declared an additional repository.  

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