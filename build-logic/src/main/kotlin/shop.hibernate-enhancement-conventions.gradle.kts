// Build-time bytecode enhancement of the JPA entities, for services with lazy associations.
// A native image cannot generate Hibernate proxies at runtime ("Generation of HibernateProxy
// instances at runtime is not allowed when the configured BytecodeProvider is 'none'"): enhanced
// entities act as their own lazy proxies. The JVM build is enhanced too, so both behave alike and
// the tests run against the same classes.
plugins {
    id("org.hibernate.orm")
}

extensions.configure<org.hibernate.orm.tooling.gradle.HibernateOrmSpec> {
    enhancement {
        enableAssociationManagement.set(false)
    }
}
