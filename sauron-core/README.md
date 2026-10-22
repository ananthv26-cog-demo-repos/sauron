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

Characterization tests (JUnit 4) pin the current behavior of `DataSet`, `PluginsConfigurationProperties`
and the PF4J extension point, so a later framework or platform migration shows behavior changes as failing tests.
Tests named `..._currentBehavior` pin behavior that looks surprising; they document it, they do not endorse it.

```bash
mvn test -Dgpg.skip=true
```

Coverage and mutation testing live in the `coverage` profile, which is off by default:

```bash
# JaCoCo report in target/site/jacoco/
mvn -Pcoverage verify -Dgpg.skip=true
# PIT report in target/pit-reports/
mvn -Pcoverage org.pitest:pitest-maven:mutationCoverage -Dgpg.skip=true
```

`lombok.config` adds Lombok's `@Generated` marker so JaCoCo and PIT skip generated `equals`/`hashCode`/builder code.
It does not change runtime behavior.

## Contributing

Please refer to [CONTRIBUTING.md](CONTRIBUTING.md) for further details.

## License

Please refer to [LICENSE](LICENSE) for further details.

