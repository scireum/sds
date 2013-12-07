var entityMap = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': '&quot;',
    "'": '&#39;',
    "/": '&#x2F;'
};

function escapeHtml(str) {
    return String(str).replace(/[&<>"'\/]/g, function (s) {
        return entityMap[s];
    });
}

function nano(template, data) {
    return template.replace(/\{([\w\.]*)\}/g, function(str, key) {
        var keys = key.split("."), v = data[keys.shift()];
        for (var i = 0, l = keys.length; i < l; i++) v = v[keys[i]];
        return (typeof v !== "undefined" && v !== null) ? escapeHtml(v) : "";
    });
}

function Printer(object) {
    this.buffer = '';
    this.isFilled = function() {
        return this.buffer != '';
    };
    this.append = function(separator, value) {
        value = nano(value, object);
        if (this.isFilled() && value != null && value != '') {
            this.buffer += separator;
        }
        if (value != null && value != '') {
            this.buffer += value;
        }
        return this;
    };

    this.add = function(value) {
        if (value != null && value != '') {
            this.buffer += nano(value, object);
        }
        return this;
    };

    this.toString = function() {
        return this.buffer;
    }

    return this;
}