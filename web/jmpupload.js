function CheckFileBrowserSupport() {
    if (window.File && window.FileReader && window.FileList && window.Blob) {
      // success!
    } else {
      alert('The File APIs are not supported in this browser.');
    }
}

function HandleFileSelect(evt) {
}

