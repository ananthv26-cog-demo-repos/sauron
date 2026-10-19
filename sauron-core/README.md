# SAURON CORE

Sauron Core library contains the common dependencies that will be used by
[Sauron plugins](https://github.com/freenowtech/sauron-service#sauron-plugin-system) and
[Sauron Service](https://github.com/freenowtech/sauron-service).

## Building

Use [maven](https://maven.apache.org/) to build the project.

```bash
mvn clean package
```

## Testing

Characterization tests pin the current behavior of `DataSet`, `PluginsConfigurationProperties` and the PF4J
extension point, so a migration (JDK, Spring Boot, Jackson, PF4J) shows behavior changes as failing tests.

```bash
mvn test
```

Coverage and mutation testing live in the opt-in `coverage` profile (off by default):

```bash
mvn -Pcoverage -Dgpg.skip clean verify                            # JaCoCo: target/site/jacoco/
mvn -Pcoverage test-compile org.pitest:pitest-maven:mutationCoverage   # PIT: target/pit-reports/
```

Run `clean` after changing `lombok.config`, otherwise stale classes are measured.

## Contributing

Please refer to [CONTRIBUTING.md](CONTRIBUTING.md) for further details.

## License

Please refer to [LICENSE](LICENSE) for further details.

