-- !Ups

UPDATE safety_warrants
SET    sent_date = null
WHERE  sent_date = '1970-01-01';

-- !Downs

UPDATE safety_warrants
SET    sent_date = '1970-01-01'
WHERE  sent_date is null;