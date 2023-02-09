/* jshint esversion:6 */

let sanctionTable;
let currentSanctionId;
let btnSave, btnUpdate, btnDelete;

function setup(){
    const sltAuth = document.getElementById("sltAuthority");
    for ( let k of Object.keys(SANCTIONS) ) {
        let opt = document.createElement("option");
        opt.innerText = k;
        opt.value = k;
        sltAuth.appendChild(opt);
    }
    sltAuth.selectedIndex = 0;
    updateSanctionUi(sltAuth.value);
    btnSave = document.getElementById("btnSave");
    btnDelete = document.getElementById("btnDelete");
    btnUpdate = document.getElementById("btnUpdate");

    if ( ENTITY_ID !== -1 ) {
        sanctionTable = document.getElementById("tblRegisteredSanctions");
        sanctionTable.columns = [
            { title:TITLES[0], fieldName:"authority"},
            { title:TITLES[1],
                fieldName:"applicationDate",
                viewFn: data => {
                    const comps = data.applicationDate.split("-");
                    return comps[2] + "/" + comps[1] + "/" + comps[0];
                }
            },
            { title:TITLES[2], fieldName:"reason"},
            { title:TITLES[3], fieldName:"sanctionType"},
            { title:TITLES[4], fieldName:"remarks"},
            { title:"", viewFn:d=>`<button class="btn btn-outline-secondary btn-sm" onclick='loadSanction(${d.id})'>✏️</button>`}
        ];
        loadSanctions();
    }
}

function loadSanctions() {
    new Playjax(beRoutes).using( c => c.BusinessEntityCtrl.apiListSanctionsFor(ENTITY_ID)).fetch()
        .then( e => {
            if ( ! e.ok ) {
                console.log(e);
            } else {
               return e.json();
            }
        }).then( data => sanctionTable.data=data );
}

function updateSanctionUi(newAuth) {
    const ulSanctions = document.getElementById("ulSanctionTypes");
    UiUtils.clearEmt(ulSanctions);

    let idx=0;
    for ( let k of SANCTIONS[newAuth] ) {
        // NEXT: make an li with a checkbox and a label for that chk adn appropriate values.
        let li = document.createElement("li");
        li.classList.add("form-check");
        let inp = document.createElement("input");
        inp.type = "checkbox";
        inp.id = "cb" + (++idx);
        inp.value = k;
        inp.classList.add("form-check-input");
        inp.checked=false;
        li.appendChild(inp);

        let lbl = document.createElement("label");
        lbl.innerHTML = k;
        lbl.classList.add("form-check-label");
        lbl.htmlFor = "cb" + idx;
        li.appendChild(lbl);

        ulSanctions.appendChild(li);
    }
}

/**
 * Parse the sanction currently in the controls. May return null, if the controls have invalid values.
 */
function parseSanction(){
    const sanction = {};
    sanction.id=-1;
    sanction.businessEntityId=ENTITY_ID;
    sanction.authority=document.getElementById("sltAuthority").value;
    sanction.sanctionType=[];
    sanction.reason=document.getElementById("fldReason").value;
    sanction.applicationDate=document.getElementById("fldDate").value;
    sanction.remarks=document.getElementById("fldRemarks").value;

    document.getElementById("ulSanctionTypes")
        .querySelectorAll("input[type='checkbox']")
        .forEach( x => {if (x.checked) {sanction.sanctionType.push(x.value);}} );

    if ( sanction.sanctionType.length === 0 ) {
        swal("יש לבחור לפחות סוג סנקציה אחד מהרשימה","אבל אפשר גם כמה","error");
        return;
    } else {
        sanction.sanctionType = sanction.sanctionType.join(", ");
        console.log(sanction.sanctionType);
    }

    const baseForm = document.getElementById("sanctions");
    if ( sanction.applicationDate.trim() === "" ||
        sanction.authority === "" ||
        sanction.sanctionType === ""
    ) {
        baseForm.classList.add("was-validated");
        return null;
    } else {
        baseForm.classList.remove("was-validated");
    }
    return sanction;
}

function displaySanction( sanction ) {
    currentSanctionId = sanction.id;

    document.getElementById("sltAuthority").value = sanction.authority;
    updateSanctionUi(sanction.authority);
    document.getElementById("fldReason").value = sanction.reason;
    document.getElementById("fldDate").value = sanction.applicationDate;
    document.getElementById("fldRemarks").value = sanction.remarks;

    document.getElementById("ulSanctionTypes")
        .querySelectorAll("input[type='checkbox']")
        .forEach( x => {if (sanction.sanctionType.indexOf(x.value) > -1) {x.checked=true;}} );
}

function adjustButtons(isEdit) {
    const editBtns = [btnUpdate, btnDelete];
    const newBtns = [btnSave];

    (isEdit ? editBtns : newBtns).forEach( b => b.classList.remove("d-none"));
    (isEdit ? newBtns : editBtns).forEach( b => b.classList.add("d-none"));
}

function loadSanction( sanctionId ) {
    const sanctions = sanctionTable.data;
    let sanction = sanctions.filter( x => x.id === sanctionId )[0];
    displaySanction(sanction);
    adjustButtons(true);
}

function addSanction(){
    const sanction = parseSanction();
    if ( sanction === null ) return;

    const bkgDialog = Informationals.showBackgroundProcess("Saving new sanction");
    Playjax(beRoutes).using(c=>c.BusinessEntityCtrl.apiStoreSanction(ENTITY_ID))
        .fetch(sanction)
        .then( res => {
            if ( res.ok ) {
                bkgDialog.success();
                res.json().then( data => {
                   sanctionTable.data.push(data);
                   sanctionTable.refreshRows();
                });
            } else {
                bkgDialog.dismiss();
                Informationals.makeDanger("Save failed: " + res.statusText ).show();
                console.error( res );
            }
        });
}

function updateSanction() {
    const sanction = parseSanction();
    if ( ! sanction ) return;
    sanction.id = currentSanctionId;
    const bkgDialog = Informationals.showBackgroundProcess("Updating sanction");
    Playjax(beRoutes).using(c=>c.BusinessEntityCtrl.apiStoreSanction(ENTITY_ID))
        .fetch(sanction)
        .then( res => {
            if ( res.ok ) {
                bkgDialog.success();
                res.json().then( data => {
                    const tableData = sanctionTable.data;
                    const index = tableData.findIndex( d => d.id === data.id );
                    tableData[index] = data;
                    sanctionTable.refreshRows();
                    clearCurrentSanction();
                });
            } else {
                bkgDialog.dismiss();
                Informationals.makeDanger("Update failed: " + res.statusText ).show();
                console.error( res );
            }
        });
}

function deleteSanction() {
    Informationals.makeYesNo("האם למחוק את הסנקציה הנוכחית?", "לא ניתן לבטל פעולה זו",deleteCallBack).show();
}

function deleteCallBack( res, info ) {
    info.dismiss();
    if ( res ) {
        const toast = Informationals.showBackgroundProcess("Deleting...");
        Playjax(beRoutes).using(c=>c.BusinessEntityCtrl.apiDeleteSanction(ENTITY_ID, currentSanctionId))
            .fetch()
            .then( res => {
                if ( res.ok ) {
                    toast.success();
                    const tableData = sanctionTable.data;
                    const index = tableData.findIndex( d => d.id === currentSanctionId );
                    tableData.splice(index, 1);
                    sanctionTable.refreshRows();
                    clearCurrentSanction();
                } else {
                    toast.dismiss();
                    console.log(res);
                }
            });
    }
}



function clearCurrentSanction(){
    currentSanctionId = null;
    document.getElementById("sltAuthority").selectedIndex = 0;
    updateSanctionUi(document.getElementById("sltAuthority").value);
    document.getElementById("fldReason").value = "";
    document.getElementById("fldDate").value = new Date();
    document.getElementById("fldRemarks").value = "";
    adjustButtons(false);
}