$(function () {
    $.ajax({
        url: "/notifications"
    }).done(function(data) {
        if (data.length > 0) {
            $('#alertCount').html(data.length);
            var alertsHTML = '';
            $.each(data, function(i, v) {
                alertsHTML += '<li>';
                alertsHTML += '<a href=' + v.id + '"/alerts/" class="notification-item">';
                alertsHTML += '<div class="img-col">&nbsp;</div>';
                alertsHTML += '<div class="body-col">' + v.msg + '</div>';
                alertsHTML += '</a>';
                alertsHTML += '</li>';
            });
            $('#alertList').html(alertsHTML);
        }
    }).error(function(data) {
        console.error(data);
    })
});