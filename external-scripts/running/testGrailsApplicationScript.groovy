import edu.berkeley.groovy.test.TestService

boolean isSet
try {
  isSet = grailsApplication != null
}
catch(Exception e) {
}

// return a string indicating if grailsApplication was injected or not
(isSet ? TestService.GRAILS_APPLICATION_SET : TestService.GRAILS_APPLICATION_NOT_SET)
