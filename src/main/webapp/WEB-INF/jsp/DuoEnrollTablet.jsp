<%@taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Duo Tablet Enrollment</title>
    </head>
    <body>
        <h1>Duo Tablet Enrollment</h1>
		<form:form method="post" commandName="DuoPerson">
			<table>
				<tr>
					<td>Tablet Operating System: </td>
					<td>
						<form:select path="deviceOS">
							<form:option value="NONE" label="--- Select ---"/>
							<form:options items="${tabletOSList}" />
						</form:select>
					</td>
					<td><form:errors path="deviceOS" cssClass="error" /></td>
				</tr>

				<tr>
					<td><form:label path="tabletName">Name Your Tablet:</form:label></td>
					<td><form:input path="tabletName" /></td>
					<td><form:errors path="tabletName" cssclass="error" /></td>
				</tr>
				<tr><td><br></td></tr>
			</table>
			<input type="submit" value="Register" name="enrollUserNPhone"/>
		</form:form>
    </body>
</html>
