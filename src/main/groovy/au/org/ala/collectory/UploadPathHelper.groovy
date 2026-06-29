package au.org.ala.collectory

/**
 * Utility class for constructing upload file paths with consistent naming conventions.
 * 
 * Path structures:
 * - Upload directory: {baseUploadPath}/{uid}/{fileId}/
 * - Upload file: {baseUploadPath}/{uid}/{fileId}/{filename}
 * - Temp directory: {baseUploadPath}/{uid}/tmp/{tempId}/
 * - Temp file: {baseUploadPath}/{uid}/tmp/{tempId}/{filename}
 * - External URL: {externalUrlPath}/{uid}/{fileId}/{filename}
 * 
 * This centralizes path construction to make it easy to maintain and modify the path structure
 * across the application. All services that deal with uploads should use these helper methods.
 */
class UploadPathHelper {
    
    /**
     * Extract UID from a resource object (DataResource, DataProvider, or Collection).
     * 
     * @param resource The resource object to extract UID from
     * @return The UID of the resource, or null if resource is null
     */
    static String extractUid(resource) {
        if (!resource) return null
        return resource.uid
    }
    
    /**
     * Construct an upload directory path.
     * 
     * @param baseUploadPath The base upload path from configuration (e.g. /data/collectory/upload/)
     * @param uid The resource UID (dataResourceUid, dataProviderUid, or collectionUid)
     * @param fileId The file ID (typically a timestamp)
     * @return The upload directory path with trailing separator
     */
    static String getUploadDirectory(String baseUploadPath, String uid, String fileId) {
        if (!baseUploadPath || !uid || !fileId) {
            throw new IllegalArgumentException("baseUploadPath, uid, and fileId must not be null or empty")
        }
        ensureTrailingSlash(baseUploadPath) + uid + File.separator + fileId + File.separator
    }
    
    /**
     * Construct a full upload file path.
     * 
     * @param baseUploadPath The base upload path from configuration
     * @param uid The resource UID
     * @param fileId The file ID
     * @param filename The filename
     * @return The full file path
     */
    static String getUploadFilePath(String baseUploadPath, String uid, String fileId, String filename) {
        def safeFilename = filename?.tokenize('/\\')?.last()
        if (!safeFilename || safeFilename.contains('..')) {
            throw new IllegalArgumentException("filename must be a simple file name without path segments")
        }
        getUploadDirectory(baseUploadPath, uid, fileId) + safeFilename
    }
    
    /**
     * Construct a temporary directory path.
     * 
     * Temporary directories are nested under the resource UID to keep temp files organized by resource.
     * 
     * @param baseUploadPath The base upload path from configuration
     * @param uid The resource UID
     * @param tempId The temporary identifier (typically an occurrence ID or download ID)
     * @return The temp directory path with trailing separator
     */
    static String getTempDirectory(String baseUploadPath, String uid, String tempId) {
        if (!baseUploadPath || !uid || !tempId) {
            throw new IllegalArgumentException("baseUploadPath, uid, and tempId must not be null or empty")
        }
        ensureTrailingSlash(baseUploadPath) + uid + File.separator + "tmp" + File.separator + tempId + File.separator
    }
    
    /**
     * Construct a full temporary file path.
     * 
     * @param baseUploadPath The base upload path from configuration
     * @param uid The resource UID
     * @param tempId The temporary identifier
     * @param filename The filename
     * @return The full temp file path
     */
    static String getTempFilePath(String baseUploadPath, String uid, String tempId, String filename) {
        def safeFilename = filename?.tokenize('/\\')?.last()
        if (!safeFilename || safeFilename.contains('..')) {
            throw new IllegalArgumentException("filename must be a simple file name without path segments")
        }
        getTempDirectory(baseUploadPath, uid, tempId) + safeFilename
    }
    
    /**
     * Construct an external URL for an uploaded file.
     * 
     * This is used for generating URLs that clients use to download files.
     * 
     * @param externalUrlPath The external base URL path from configuration (e.g. http://localhost/upload)
     * @param uid The resource UID
     * @param fileId The file ID
     * @param filename The filename
     * @return The external URL for the file
     */
    static String getExternalUrl(String externalUrlPath, String uid, String fileId, String filename) {
        if (!externalUrlPath || !uid || !fileId || !filename) {
            throw new IllegalArgumentException("externalUrlPath, uid, fileId, and filename must not be null or empty")
        }
        ensureTrailingSlash(externalUrlPath) + uid + "/" + fileId + "/" + filename
    }
    
    /**
     * Construct a combined directory parameter for URL routing.
     * 
     * This is used to combine uid and fileId into a single parameter that can be passed
     * through URL mappings as the $directory parameter. This allows the file download
     * controller to work with both new (uid/fileId/filename) and old (fileId/filename) formats.
     * 
     * @param uid The resource UID
     * @param fileId The file ID
     * @return A combined directory string in the format "uid/fileId"
     */
    static String getCombinedDirectory(String uid, String fileId) {
        if (!uid || !fileId) {
            throw new IllegalArgumentException("uid and fileId must not be null or empty")
        }
        uid + "/" + fileId
    }
    
    /**
     * Ensure a path string ends with a file separator.
     * 
     * @param path The path string
     * @return The path with a trailing separator, or the original path if it was null
     */
    private static String ensureTrailingSlash(String path) {
        if (!path) return path
        path.endsWith(File.separator) ? path : path + File.separator
    }
}
