<asset:script type="text/javascript">
  var CHARTS_CONFIG = {
      biocacheServicesUrl: "${grailsApplication.config.biocacheServicesUrl}",
      biocacheWebappUrl: "${grailsApplication.config.biocacheUiURL}",
      bieWebappUrl: "${grailsApplication.config.bieUiURL}",
      collectionsUrl: "${grailsApplication.config.grails.serverURL}"
  };

  // records
  if (${!instance.hasProperty('resourceType') || instance.resourceType == 'records'  || instance.resourceType == 'events'}) {
      // summary biocache data
      var queryUrl = CHARTS_CONFIG.biocacheServicesUrl + "/occurrences/search?pageSize=0&q=${facet}:${instance.uid}";

      $.ajax({
        url: queryUrl,
        dataType: 'json',
        timeout: 30000,
        complete: function(jqXHR, textStatus) {
            if (textStatus == 'timeout') {
                noData();
                alert('Sorry - the request was taking too long so it has been cancelled.');
            }
            if (textStatus == 'error') {
                noData();
                alert('Sorry - the records breakdowns are not available due to an error.');
            }
        },
        success: function(data) {
            // check for errors
            if (data.length == 0 || data.totalRecords == undefined || data.totalRecords == 0) {
                noData();
            } else {
                setNumbers(data.totalRecords);
                if (data.totalRecords > 0){
                    $('#dataAccessWrapper').css({display:'block'});
                    $('#totalRecordCountLink').html(data.totalRecords.toLocaleString() + " ${g.message(code: 'public.show.rt.des03')}");
                }
            }
        }
      });
  }

  if (${instance.hasProperty('resourceType') && instance.resourceType == 'events' && org.apache.commons.lang.StringUtils.isNotEmpty(grailsApplication.config.eventsURL ?: '')}) {
      // summary events data
      var queryUrl = '${grailsApplication.config.eventsURL}';
      var body = {
          query: 'query list($datasetKey: JSON){' +
            'eventSearch(predicate: {type: equals, key: \"datasetKey\", value: $datasetKey}) {' +
              'documents(size: 1) {total}' +
            '}' +
          '}',
          variables: '{"datasetKey":"${instance.uid}"}'
      }

      $.ajax({
        url: queryUrl,
        dataType: 'json',
        data: body,
        timeout: 30000,
        complete: function(jqXHR, textStatus) {
            if (textStatus == 'timeout' || textStatus == 'error') {
                // noData();
            }
        },
        success: function(data) {
            if (data.data.eventSearch.documents.total > 0){
                $('#eventRecordsWrapper').css({display:'block'});
                $('#totalEventCount').html(data.data.eventSearch.documents.total.toLocaleString() + " ${g.message(code: 'public.show.rt.des08')}");
            }
        }
      });
  }

</asset:script>

<asset:script type="text/javascript">
    <charts:biocache
            biocacheServiceUrl="${grailsApplication.config.biocacheServicesUrl}"
            biocacheWebappUrl="${grailsApplication.config.biocacheUiURL}"
            q="${facet}:${instance.uid}"
            qc=""
            fq=""
    />
</asset:script>
