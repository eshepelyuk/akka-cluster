import ch.qos.logback.classic.encoder.PatternLayoutEncoder

import static ch.qos.logback.classic.Level.*

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%date{ISO8601} %-5level %logger{36} - %msg%n"
    }
}

logger("com.newagesol", DEBUG)
logger("akka.cluster", INFO)
logger("akka.serialization", INFO)
logger("akka.remote.EndpointWriter", INFO)
logger("akka", DEBUG)
root(INFO, ["STDOUT"])