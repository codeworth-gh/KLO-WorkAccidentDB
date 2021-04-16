/* jshint esversion:6 */

function updateForm(fieldName){
    const values = [];
    const emts = document.getElementsByName(fieldName+"Chk");
    for ( let e in emts ) {
        if ( emts[e].checked ) {
            values.push( emts[e].value );
        }
    }
    document.getElementsByName(fieldName)[0].value = values.join(",");
}

function submitFilter() {
    console.log("Submitting");
    document.forms.filterForm.submit();
}