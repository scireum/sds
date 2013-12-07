var redirectToJoin = false;
$(document).ready(function(e) {
    if(window.location.hash && window.location.hash.substr(0,2) == '#!') {
        localStorage.currentRoom = window.location.hash.substr(2);
    }
    if (localStorage.uid != null) {
        if (localStorage.currentRoom) {
            enterTheVoid();
        } else {
            $.mobile.changePage('#main');
        }
    } else if (localStorage.currentRoom) {
        redirectToJoin = true;
    }
});

function createIdentity() {
    $.getJSON("/service/json/CreateIdentity", function( data ) {
        localStorage.uid = data.id;
        localStorage.privateKey = data.privateKey;
        $.mobile.changePage('#editAccount');
    });
}

function prepareAuthUrl(service) {
    if (localStorage.uid == null) {
        $.mobile.changePage('#login');
        return null;
    }
    var time = new Date().getTime();
    return service+"?user="+localStorage.uid+"&accessCode="+encodeURI(md5(localStorage.uid+localStorage.privateKey+time))+"&timestamp="+time;
}

function editAccount() {
    var url = prepareAuthUrl("/service/json/ReadIdentity");
    if (url == null) {
        return;
    }
    $.getJSON(url, function( data ) {
        $('#editAccountName').val(data.name);
        $('#editAccountCompany').val(data.company);
        $('#editAccountPosition').val(data.position);
        $('#editAccountPhone').val(data.phone);
        $('#editAccountMail').val(data.email);
        $.mobile.changePage('#editAccount');
    });
}

function updateAccount() {
    var url = prepareAuthUrl("/service/json/UpdateIdentity");
    if (url == null) {
        return;
    }
    url += "&name="+encodeURI($('#editAccountName').val());
    url += "&company="+encodeURI($('#editAccountCompany').val());
    url += "&position="+encodeURI($('#editAccountPosition').val());
    url += "&phone="+encodeURI($('#editAccountPhone').val());
    url += "&email="+encodeURI($('#editAccountMail').val());
    $.getJSON(url, function( data ) {
        if (localStorage.currentRoom && redirectToJoin) {
            redirectToJoin = false;
            enterTheVoid();
        } else {
            $.mobile.changePage('#main');
        }
    });
}

function prepareJoinRoom() {
    if (localStorage.currentRoom) {
        $('#joinRoomCode').val(localStorage.currentRoom);
    }
    $.mobile.changePage('#joinRoom');
}

function joinRoom() {
    localStorage.currentRoom = $('#joinRoomCode').val();
    enterTheVoid();
}

function refreshRoom() {
    enterTheVoid();
}

function autorefreshRoom() {
    if ($.mobile.activePage.attr("id") =='room') {
        enterTheVoid();
    }
}

function enterTheVoid() {
    var url = prepareAuthUrl("/service/json/EnterRoom");
    if (url == null) {
        return;
    }
    url += "&code="+encodeURI(localStorage.currentRoom);
    $.getJSON(url, function( data ) {
        if (!data.success) {
            delete localStorage.currentRoom;
            $.mobile.changePage('#joinRoom');
        } else {
            $('#roomTokenImgContainer').hide();
            $('#roomName').html(data.name);
            $('#roomLocation').html(data.location);
            $('#roomTokenLink').html(data.token);
            $('#roomTokenImg').attr('src', data.qrURL);
            $('#roomQuests').html('');
            if (data.editable) {
                $('#roomToken').show();
            } else {
                $('#roomToken').hide();
            }
            for (var i = 0; i < data.guests.length; i++) {
                guest = data.guests[i];
                $(new Printer(guest).add('<li><img src="{avatar}" style="border-radius: 40px; margin-left: 4px;margin-top:4px" /><h2>{name}</h2><p>{company}')
                    .append(', ','{position}')
                    .append('<br /><b>Points.:</b> ','{points}')
                    .append('<br /><b>Tel.:</b> ','{phone}')
                    .append('<br /><b>Mobile.:</b> ','{mobile}')
                    .append('<br /><b>eMail.:</b> ','{email}')
                    .add('</p><div style="clear: both"></div></li>').toString()).appendTo($('#roomQuests'));
            }
            $('#roomQuests').listview('refresh');
            $.mobile.changePage('#room');
            setTimeout(autorefreshRoom, 10000);
        }
    });
}

function createRoom() {
    var url = prepareAuthUrl("/service/json/CreateRoom");
    if (url == null) {
        return;
    }
    url += "&name="+encodeURI($('#createRoomName').val());
    url += "&location="+encodeURI($('#createRoomLocation').val());
    $.getJSON(url, function( data ) {
        if (data.error) {
            $.mobile.changePage('#main');
        } else {
            $('#createRoomName').val('');
            $('#createRoomLocation').val('');
            localStorage.currentRoom = data.token;
            enterTheVoid();
        }
    });
}

function killIdentity() {
    delete localStorage.uid;
    delete localStorage.privateKey;
    $.mobile.changePage('#login');
}