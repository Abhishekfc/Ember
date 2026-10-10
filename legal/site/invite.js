// The invite page: shows who invited you and sends you to Google Play, handing their username to the
// app through Play's install referrer so it can offer to add them as a friend.
//
// The two helpers are plain functions so they can be tested on their own (see the bottom).

var PLAY_URL = "https://play.google.com/store/apps/details?id=com.emigo.app";

// Same rule the Emigo server enforces for a username: letters, numbers, "_" and ".".
function usernameFromPath(pathname) {
  var match = /^\/i\/([A-Za-z0-9_.]{1,64})\/?$/.exec(pathname || "");
  return match ? match[1] : null;
}

function playLink(username) {
  if (!username) return PLAY_URL;
  // Play passes "referrer" to the app after the install; the app reads "invite=<username>".
  return PLAY_URL + "&referrer=" + encodeURIComponent("invite=" + username);
}

function isIPhoneOrIPad(userAgent) {
  return /iPhone|iPad|iPod/i.test(userAgent || "");
}

if (typeof document !== "undefined") {
  var username = usernameFromPath(location.pathname);
  var headline = document.getElementById("headline");
  var play = document.getElementById("play");
  var soon = document.getElementById("soon");

  // textContent, never innerHTML: the name comes from the address bar.
  if (username) headline.textContent = "@" + username + " invited you to Emigo";

  if (isIPhoneOrIPad(navigator.userAgent)) {
    play.hidden = true;
    soon.hidden = false;
  } else {
    play.href = playLink(username);
  }
}

if (typeof module !== "undefined") {
  module.exports = { usernameFromPath: usernameFromPath, playLink: playLink, isIPhoneOrIPad: isIPhoneOrIPad };
}
