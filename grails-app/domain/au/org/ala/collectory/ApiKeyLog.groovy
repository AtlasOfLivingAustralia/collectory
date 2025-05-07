package au.org.ala.collectory

class ApiKeyLog {
    String remoteReferer
    String remoteAddr
    String remoteHost
    String remoteForwardedFor
    String controllerName
    String actionName
    Date lastCalled = new Date()

    static constraints = {
        remoteReferer nullable: true
        remoteForwardedFor nullable: true
        controllerName nullable: true
        actionName nullable: true
        remoteAddr nullable: false
        remoteHost nullable: false
        lastCalled nullable: false
    }
}