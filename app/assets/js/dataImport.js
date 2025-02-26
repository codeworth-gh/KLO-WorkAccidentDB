/* jshint esversion:6 */
let btnImportSW;
let fileSW;
let pj;
let curSafetyMonitorId="";
let clpSafetyImportMonitor;

function setup() {
    btnImportSW = document.getElementById("btnSafetyWarrants");
    fileSW = document.getElementById("fileSafetyWarrants");

    fileSW.onchange = swFileChanged;
    btnImportSW.onclick = startImport;

    pj = new Playjax(beRoutes);
    clpSafetyImportMonitor = new bootstrap.Collapse("#safetyImportMonitor", {toggle:false});
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
    curSafetyMonitorId = data.id;
    window.setTimeout(safetyMonitor, 2000);
    clpSafetyImportMonitor.show();
    Informationals.loader.dismiss();
}

function safetyMonitor() {
    pj.using(c=>c.DataImportCtrl.apiSafetyImportStatus(curSafetyMonitorId))
        .fetch()
        .then( r => {
            if ( r.ok ) {
                r.json().then( monitorData => {
                    for ( let d of ["status", "added", "ignored", "existed", "errorCount"]) {
                        let emt = document.getElementById(d+"Monitor");
                        if ( ! emt ) {
                            console.error("Missing element " + d+"Monitor" );
                        } else {
                            emt.innerText = monitorData[d];
                            UiUtils.highlight(emt);
                        }
                    }
                    if ( monitorData.status === "Pending" || monitorData.status === "Started" ) {
                        window.setTimeout(safetyMonitor, 2000);
                    } else if ( monitorData.status === "Error" && monitorData.message && monitorData.message.trim() !== "") {
                        const msgEmt = document.getElementById("importErrorMessage");
                        msgEmt.innerText = monitorData.message;
                        msgEmt.classList.remove("d-none");
                    }
                });
            }
        }).catch( e => console.error(e) );
}