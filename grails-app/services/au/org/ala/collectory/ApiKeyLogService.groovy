package au.org.ala.collectory

class ApiKeyLogService {
    ApiKeyLog save(ApiKeyLog log) {
       ApiKeyLog.withTransaction {
            log.save(flush: true, failOnError: true)
        }
        return log
    }

    List<ApiKeyLog> list(Map params) {
        return ApiKeyLog.list(params)
    }

    ApiKeyLog get(Long id) {
        return ApiKeyLog.get(id)
    }

    boolean delete(Long id) {
        def log = ApiKeyLog.get(id)
        if (log) {
            log.delete(flush: true)
            return true
        }
        return false
    }

    def deleteAll() {
        ApiKeyLog.executeUpdate("delete from ApiKeyLog")
    }
}
