{{- define "mysql.labels" }}
  labels:
    generator: helm
    date: {{ now | htmlDate }}
{{- end }}
