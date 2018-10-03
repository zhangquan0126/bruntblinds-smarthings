/**
 *  Blinds
 *
 *  Copyright 2018 Quan Zhang
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Blinds Controller", namespace: "zhangquan0126", author: "Quan Zhang") {
		capability "Window Shade"
        capability "Switch Level" // so that setLevel command can be registered
        capability "Refresh"
	}


	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
        multiAttributeTile(name:"blinds", type: "windowShade", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.blinds", key: "PRIMARY_CONTROL") {
                attributeState "open", label:'${name}', action:"close", icon:"st.Weather.weather14", backgroundColor:"#ffcc33",
				nextState:"waiting"
				attributeState "closed", label:'${name}', action:"open", icon:"st.Seasonal Winter.seasonal-winter-011", backgroundColor:"#bbbbdd",
				nextState:"waiting"
				attributeState "waiting", label:'${name}', action:"refresh", icon:"st.Seasonal Winter.seasonal-winter-011", backgroundColor:"#15EE10",
				nextState:"waiting"
				attributeState "commsError", label:'Comms Error', action:"refresh", icon:"st.Seasonal Winter.seasonal-winter-011", backgroundColor:"#ffa81e",
				nextState:"waiting"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "open", action:"setLevel"
                attributeState "closed", action:"setLevel"
            }
            tileAttribute ("deviceError", key: "SECONDARY_CONTROL") {
				attributeState "deviceError", label: '${currentValue}'
	    	}
        }

        standardTile("open", "device.open", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("closed", label:'open', action:"open", icon:"st.Weather.weather14")
        }
        standardTile("close", "device.close", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("open", label:'close', action:"close", icon:"st.Seasonal Winter.seasonal-winter-011")
        }
        standardTile("preset", "device.preset", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("open", label:'preset', action:"presetPosition", icon:"st.Transportation.transportation13")
            state("closed", label:'preset', action:"presetPosition", icon:"st.Transportation.transportation13")
            state("default", label:'preset', action:"presetPosition", icon:"st.Transportation.transportation13")
        }
        
        controlTile("slider", "device.level", "slider", height: 2,
                 width: 2, range:"(0..100)", inactiveLabel: false) {
        	state "open", action:"setLevel"
            state "closed", action:"setLevel"
    	}

        standardTile("refresher", "device.refresh", width:2, height:2, decoration: "flat", inactiveLabel: false) {
            state "open", label:'', action:"refresh", icon:"st.secondary.refresh"   
            state "closed", label:'', action:"refresh", icon:"st.secondary.refresh"
        } 
		
		main("blinds")
		details("blinds", "open", "close", "refresher")
	}
}

def installed() {
	updated()
}

def updated() {
	unschedule()
    state.requestPosition = -1
	switch(refreshRate) {
		case "5":
			runEvery5Minutes(refresh)
			log.info "Refresh Scheduled for every 5 minutes"
			break
		case "10":
			runEvery10Minutes(refresh)
			log.info "Refresh Scheduled for every 10 minutes"
			break
		case "15":
			runEvery15Minutes(refresh)
			log.info "Refresh Scheduled for every 15 minutes"
			break
		default:
			runEvery30Minutes(refresh)
			log.info "Refresh Scheduled for every 30 minutes"
	}
	runIn(2, refresh)
}

void uninstalled() {
	def alias = device.label
	log.debug "Removing device ${alias} with DNI = ${device.name}"
	parent.removeChildDevice(alias, device.name)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'windowShade' attribute

}

// handle commands
def open() {
	log.debug "Executing 'open'"
	// handle 'open' command
    state.requestPosition = 100
    sendCmdtoServer("openCloseResponse")
}

def close() {
	log.debug "Executing 'close'"
	// handle 'close' command
    state.requestPosition = 0
    sendCmdtoServer("openCloseResponse")
}

def setLevel(level) {
    log.debug "Executing 'setLevel' ${device.latestValue("level")}"
    state.requestPosition = level
    sendCmdtoServer("openCloseResponse")
}

def openCloseResponse(cmdResponse){
    log.debug "openCloseResponse cmdResponse ${cmdResponse}"
    refresh()
}

def refresh(){
    log.debug "refresh"
	sendCmdtoServer("refreshResponse")
}

def refreshResponse(cmdResponse){
    if (cmdResponse != null) {
    	log.debug "cmdResponse ${cmdResponse.toString()}"
        log.debug "cmdResponse[currentPosition] ${cmdResponse.currentPosition}"
    }
	state.currentPosition = cmdResponse.currentPosition
    def status = ""
    log.debug "state.requestPosition ${state.requestPosition}"
    if (state.requestPosition != -1 && state.currentPosition != state.requestPosition.toString()) {
        status = "waiting"
    } else if (state.currentPosition == "0") {
		status = "closed"
	} else {
		status = "open"
	}
	log.info "${device.name} ${device.label}: Power: ${status}"
	sendEvent(name: "blinds", value: status)
    sendEvent(name: "level", value: state.currentPosition)
    if (status == "waiting") {
        runIn(2, refresh)
    }
}

def presetPosition() {
	log.debug "Executing 'presetPosition'"
	// handle 'presetPosition' command
    state.requestPosition = 0
    sendCmdtoServer("openCloseResponse")
}

//	----- SEND COMMAND TO CLOUD VIA SM -----
private sendCmdtoServer(action){
	def appServerUrl = getDataValue("appServerUrl")
	def deviceId = getDataValue("deviceId")
    def sessionId = getDataValue("sessionId")
	def cmdResponse = ""
    if (action.find("refresh")) {
    	cmdResponse = parent.sendDeviceCmd(appServerUrl, deviceId, sessionId, -1)
    } else {
        log.debug "set position"
        cmdResponse = parent.sendDeviceCmd(appServerUrl, deviceId, sessionId, state.requestPosition)
    }
    log.debug "cmdResponse from parent is ${cmdResponse}"
	String cmdResp = cmdResponse.toString()
	if (cmdResp.find("ERROR")){
		def errMsg = cmdResp.substring(7,cmdResp.length())
		log.error "${device.name} ${device.label}: ${errMsg}"
		sendEvent(name: "blinds", value: "commsError", descriptionText: errMsg)
		sendEvent(name: "deviceError", value: errMsg)
		action = ""
	} else {
		sendEvent(name: "deviceError", value: "OK")
	}	
	switch(action) {
		case "openCloseResponse":
			openCloseResponse(cmdResponse)
			break

		case "refreshResponse":
			refreshResponse(cmdResponse)
			break

		default:
			log.debug "at default"
	}
}

//	----- CHILD / PARENT INTERCHANGE TASKS -----
def syncAppServerUrl(newAppServerUrl) {
	updateDataValue("appServerUrl", newAppServerUrl)
		log.info "Updated appServerUrl for ${device.name} ${device.label}"
}