WAIT_INTERVAL = 1000 # ms after one request completes before another starts

$ ->
  $('body.document-set-index li.unfinished').each ->
    $li = $(this)
    $a = $li.find('h2 a')
    json_href = "#{$a.attr('href')}.json"

    done = (data) ->
      $li.replaceWith(data.html)

    state_description = (data) ->
      if data.n_jobs_ahead_in_queue
        i18n("views.DocumentSet._documentSet.jobs_to_process", data.n_jobs_ahead_in_queue)
      else
        data.state_description

    progress = (data) ->
      $li.find('progress').attr('value', data.percent_complete)
      $li.find('.state').text(data.state)
      $li.find('.state-description').text(state_description(data))

    refresh = ->
      ajax = $.ajax({
        url: json_href
        cache: false
      })
      ajax.done (data) ->
        if data.html?
          done(data)
        else
          progress(data)
          window.setTimeout(refresh, WAIT_INTERVAL)

    $a.click((e) -> e.preventDefault())
    window.setTimeout(refresh, WAIT_INTERVAL)
