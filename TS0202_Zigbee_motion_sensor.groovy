metadata {
    definition(name: "TS0202 Zigbee motion sensor", namespace: "james_aliex", author: "James McMurran", importUrl: "") {
        capability "Battery"
        capability "Motion Sensor"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"

        command "refreshBattery"
        command "enrollResponse"

        fingerprint profileId: "0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_lf56vpxj"
    }

    preferences {
        input name: "enableDebug", type: "bool", title: "Enable Debug Logging", description: "Turn on debug logging", defaultValue: true
    }
}

def parse(String description) {
    if (enableDebug) log.debug "Received message: ${description}"
    
    if (description?.startsWith("zone status")) {
        handleZoneStatus(description)
    } else {
        def descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap) {
            if (enableDebug) log.debug "Parsed map: ${descMap}"

            // Handle specific IAS Zone attributes for motion
            if (descMap.cluster == "0500") {
                if (descMap.attrId == "0013" || descMap.attrId == "F001") {
                    def motionState = descMap.value == "01" ? "active" : "inactive"
                    log.info "Motion detected: ${motionState} (attribute ${descMap.attrId})"
                    sendEvent(name: "motion", value: motionState)
                }
            } 
            // Handle Battery voltage attribute
            else if (descMap.cluster == "0001" && descMap.attrId == "0021") {
                def batteryVoltage = zigbee.convertHexToInt(descMap.value)
                log.info "Battery Voltage: ${batteryVoltage}V"
                sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")
            }
        } else {
            log.warn "Unparsed message: ${description}"
        }
    }
}

// Handle IAS Zone status messages for motion
def handleZoneStatus(description) {
    if (enableDebug) log.debug "Handling zone status: ${description}"
    def isActive = description.contains("0x0001")
    def motionState = isActive ? "active" : "inactive"
    log.info "Motion status from zone status: ${motionState}"
    sendEvent(name: "motion", value: motionState)
}

def refreshBattery() {
    if (enableDebug) log.debug "Requesting battery voltage"
    zigbee.readAttribute(0x0001, 0x0021)  // Battery Voltage
}

// Respond to enroll request for IAS Zone
def enrollResponse() {
    if (enableDebug) log.debug "Sending IAS Zone enroll response"
    def cmds = zigbee.enrollResponse()
    return cmds
}

def configure() {
    if (enableDebug) log.debug "Configuring reporting for IAS Zone and battery"
    def cmds = []
    
    // IAS Zone - Motion Detection
    cmds += zigbee.enrollResponse()
    cmds += zigbee.configureReporting(0x0500, 0x0002, 0x20, 30, 300, null)  // IAS Zone Status reporting

    // Battery Reporting
    cmds += zigbee.batteryConfig()

    return cmds
}

def refresh() {
    if (enableDebug) log.debug "Refreshing attributes"
    refreshBattery()
    enrollResponse()
}
