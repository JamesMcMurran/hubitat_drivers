metadata {
    definition(name: "TS0201 Zigbee Temperature and Humidity Sensor", namespace: "jamesalix", author: "James McMurran", importUrl: "") {
        capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"

        command "refreshBattery"

        fingerprint profileId: "0104", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A", model:"TS0201", manufacturer:"_TZ3000_fllyghyj"
    }

    preferences {
        input name: "enableDebug", type: "bool", title: "Enable Debug Logging", description: "Turn on debug logging", defaultValue: true
        input name: "tempMinInterval", type: "number", title: "Temperature Min Reporting Interval (seconds)", defaultValue: 30
        input name: "tempMaxInterval", type: "number", title: "Temperature Max Reporting Interval (seconds)", defaultValue: 300
        input name: "tempChange", type: "decimal", title: "Temperature Reportable Change (°C)", defaultValue: 0.1
        input name: "humidityMinInterval", type: "number", title: "Humidity Min Reporting Interval (seconds)", defaultValue: 30
        input name: "humidityMaxInterval", type: "number", title: "Humidity Max Reporting Interval (seconds)", defaultValue: 300
        input name: "humidityChange", type: "decimal", title: "Humidity Reportable Change (%)", defaultValue: 1.0
    }
}

def parse(String description) {
    if (enableDebug) log.debug "Received message: ${description}"

    def descMap = zigbee.parseDescriptionAsMap(description)
    if (descMap) {
        if (enableDebug) log.debug "Parsed map: ${descMap}"

        // Handle Temperature Measurement
        if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            def temperature = zigbee.convertHexToInt(descMap.value) / 100.0
            log.info "Temperature: ${temperature}°C"
            sendEvent(name: "temperature", value: temperature, unit: "°C")
        }

        // Handle Humidity Measurement
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            def humidity = zigbee.convertHexToInt(descMap.value) / 100.0
            log.info "Humidity: ${humidity}%"
            sendEvent(name: "humidity", value: humidity, unit: "%")
        }

        // Handle Battery Voltage
        else if (descMap.cluster == "0001" && descMap.attrId == "0021") {
            def batteryVoltage = zigbee.convertHexToInt(descMap.value) / 10.0
            log.info "Battery Voltage: ${batteryVoltage}V"
            sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")
        }
    } else {
        log.warn "Unparsed message: ${description}"
    }
}

def refreshBattery() {
    if (enableDebug) log.debug "Requesting battery voltage"
    zigbee.readAttribute(0x0001, 0x0021)  // Battery Voltage
}

def configure() {
    if (enableDebug) log.debug "Configuring reporting for Temperature, Humidity, and Battery"
    def cmds = []

    // Convert user preferences to Integer for configureReporting method
    def minTempInterval = tempMinInterval.toInteger()
    def maxTempInterval = tempMaxInterval.toInteger()
    def tempChangeHex = (tempChange * 100).toInteger()  // Convert to centi-degrees and to Integer

    def minHumidityInterval = humidityMinInterval.toInteger()
    def maxHumidityInterval = humidityMaxInterval.toInteger()
    def humidityChangeHex = (humidityChange * 100).toInteger()  // Convert to centi-percent and to Integer

    // Temperature Reporting
    cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, minTempInterval, maxTempInterval, tempChangeHex)
    
    // Humidity Reporting
    cmds += zigbee.configureReporting(0x0405, 0x0000, 0x21, minHumidityInterval, maxHumidityInterval, humidityChangeHex)

    // Battery Reporting
    cmds += zigbee.batteryConfig()

    return cmds
}


def refresh() {
    if (enableDebug) log.debug "Refreshing attributes"
    refreshBattery()
    zigbee.readAttribute(0x0402, 0x0000)  // Temperature Measurement
    zigbee.readAttribute(0x0405, 0x0000)  // Humidity Measurement
}
