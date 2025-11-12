{{- define "heatwise-ui.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "heatwise-ui.fullname" -}}
{{- printf "%s" (include "heatwise-ui.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "heatwise-ui.labels" -}}
app.kubernetes.io/name: {{ include "heatwise-ui.name" . }}
app.kubernetes.io/instance: {{ include "heatwise-ui.fullname" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: "helm"
{{- range $k, $v := .Values.labels }}
{{ $k }}: {{ $v | quote }}
{{- end }}
{{- end -}}