/* jshint esversion:6 */
let btnImportSW;
let fileSW;

function setup() {
    btnImportSW = document.getElementById("btnSafetyWarrants");
    fileSW = document.getElementById("fileSafetyWarrants");

    fileSW.onchange = swFileChanged;
    btnImportSW.onclick = startImport;
}

function swFileChanged(e) {
    if ( fileSW.files[0] ) {
        btnImportSW.disabled = false;
    }
}

function startImport() {
    const theFile = fileSW.files[0];
    if ( theFile === undefined ) return;

    try {
        btnImportSW.disabled = true;
        Informationals.loader("Setup");
        const form = new FormData();
        form.append("csvFile", theFile);
        const payload = {
            method: "POST",
            body: form,
            headers: new Headers()
        };
        payload.headers.append("Csrf-Token", document.getElementById("Playjax_csrfTokenValue").innerText);
        const destUrl = beRoutes.controllers.DataImportCtrl.apiImportSafetyWarrants().url;

        fetch(destUrl, payload).then( res => {
            if ( res.ok ) {
                Informationals.loader("Processing");
                res.json().then( json=>startImportMonitor(json) );
            } else {
                console.error(res);
                res.text().then( t => {
                    Informationals.makeDanger("Error Creating Import", t).show();
                });
            }
        });

    } catch (e) {
        console.error(e);
        Informationals.makeDanger(e.message).show();
    }
}

function startImportMonitor( data ) {
    console.info(data);
}